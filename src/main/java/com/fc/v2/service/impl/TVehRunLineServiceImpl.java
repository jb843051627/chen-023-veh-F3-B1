package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TVehRunLineMapper;
import com.fc.v2.model.auto.TVehRunLine;
import com.fc.v2.model.custom.RunLineDefect;
import com.fc.v2.model.custom.RunLineVerdict;
import com.fc.v2.service.ITVehRunLineService;

/**
 * 运行界次判定线 Service业务层处理。
 *
 * 界次归层四问只由本类 verdict 一处作答：折算口径、在位口径、说话次序、三层归层都在这里，
 * 屏上不许另摆第二套；在位名单（listEffective）与裁决请界走同一处筛选，两本账不许有。
 *
 * 旧办法（evaluate/evaluateTop/listAvailable/usable/countAvailable）照原样摆着，
 * 这一回的新需求一律另起名目，不往旧办法身上挂新参数。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TVehRunLineServiceImpl implements ITVehRunLineService {

    /** 界册分界列到三位小数，取数折完也到三位小数，比数两边才对得齐 */
    private static final int REGISTER_SCALE = 3;
    /** 取数与比数同一把舍法：四舍五入；不许一处四舍五入、一处直接截尾（去年冬季压线那条踩过） */
    private static final RoundingMode SHARED_ROUNDING = RoundingMode.HALF_UP;
    /** 车速：每秒多少米 ×3.6 折成每小时多少公里（只往界册那一头折） */
    private static final BigDecimal SPEED_FACTOR = new BigDecimal("3.6");
    /** 连续驾驶时长：分钟 ÷60 折成小时（只往界册那一头折） */
    private static final BigDecimal MINUTES_PER_HOUR = new BigDecimal("60");

    /** 说话次序：交班次序降序；序相同再比界次代号，照排法落在末位（代号降序）的胜出 */
    private static final Comparator<TVehRunLine> SPEAK_ORDER = new Comparator<TVehRunLine>() {
        @Override
        public int compare(TVehRunLine a, TVehRunLine b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            return cb.compareTo(ca);
        }
    };

    @javax.annotation.Resource
    private TVehRunLineMapper vehRunLineMapper;

    // ------------------------------------------------------------------
    // 旧办法（凭界次代号取值）：照原样摆着，不挂新参数、不改口径
    // ------------------------------------------------------------------

    @Override
    public TVehRunLine selectTVehRunLineById(Long id) {
        return this.vehRunLineMapper.selectById(id);
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        TVehRunLine rule = findRule(ruleCode, at);
        return levelOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return 0;
        }
        if (input.compareTo(BigDecimal.ZERO) < 0
                || input.compareTo(new BigDecimal("100")) > 0) {
            return 0;
        }
        List<TVehRunLine> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return 0;
        }
        return levelOf(avail.get(0), input);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TVehRunLine r = this.vehRunLineMapper.selectById(id);
        return r != null;
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        return this.vehRunLineMapper
                .selectList(new QueryWrapper<TVehRunLine>().eq("del_flag", 0)).size();
    }

    /** 旧口径：只排除留痕删除，不做生效窗口（新走法不要用它） */
    @Override
    public List<TVehRunLine> listAvailable(Date at) {
        if (at == null) {
            return new ArrayList<TVehRunLine>();
        }
        return this.vehRunLineMapper.selectList(new QueryWrapper<TVehRunLine>().eq("del_flag", 0));
    }

    private TVehRunLine findRule(String ruleCode, Date at) {
        List<TVehRunLine> hit = this.vehRunLineMapper.selectList(new QueryWrapper<TVehRunLine>()
                .eq("del_flag", 0).eq("rule_code", ruleCode));
        TVehRunLine newest = null;
        for (TVehRunLine r : hit) {
            if (newest == null || r.getEffEnd() == null) {
                newest = r;
            } else if (newest.getEffEnd() != null && r.getEffEnd().after(newest.getEffEnd())) {
                newest = r;
            }
        }
        return newest;
    }

    /** 旧分档：等于上限取高一档（旧口径，新走法不走这里） */
    private int levelOf(TVehRunLine rule, BigDecimal input) {
        if (rule == null) {
            return 0;
        }
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) <= 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) <= 0) {
            return 2;
        }
        if (rule.getTh3Max() != null && input.compareTo(rule.getTh3Max()) <= 0) {
            return 3;
        }
        return 4;
    }

    // ------------------------------------------------------------------
    // 新走法：界次归层四问只此一处
    // ------------------------------------------------------------------

    @Override
    public RunLineVerdict verdict(String measureKind, BigDecimal rawValue, Date at) {
        if (!isKnownMeasure(measureKind) || rawValue == null || at == null) {
            return null;
        }
        // 1) 取数：只往界册那一头折，取数一处、比数一处，同一把舍法
        BigDecimal converted = convertToRegister(measureKind, rawValue);

        // 2)+3) 请界与说话：在位名单与屏上同出一处，排定了头一个就是说话的
        List<TVehRunLine> effective = listEffective(measureKind, at);
        if (effective.isEmpty()) {
            // 四条界一条都够不上：不给层，代号与起用日两栏一并留空，不许抄旁边那条
            return new RunLineVerdict(TEXT_NO_RULE, null, null, null, converted);
        }
        TVehRunLine speaker = effective.get(0);

        // 4) 归层只有一套说法，压线归本层：
        //    [起算,够线)提示、[够线,封顶)严管、[封顶及以上)立案
        if (converted.compareTo(speaker.getTh1Max()) < 0) {
            return new RunLineVerdict(TEXT_BELOW_START, null,
                    speaker.getRuleCode(), speaker.getEffStart(), converted);
        }
        if (converted.compareTo(speaker.getTh2Max()) < 0) {
            return new RunLineVerdict(levelName(LEVEL_TIP), LEVEL_TIP,
                    speaker.getRuleCode(), speaker.getEffStart(), converted);
        }
        if (converted.compareTo(speaker.getTh3Max()) < 0) {
            return new RunLineVerdict(levelName(LEVEL_STRICT), LEVEL_STRICT,
                    speaker.getRuleCode(), speaker.getEffStart(), converted);
        }
        return new RunLineVerdict(levelName(LEVEL_CASE), LEVEL_CASE,
                speaker.getRuleCode(), speaker.getEffStart(), converted);
    }

    @Override
    public List<TVehRunLine> listEffective(String measureKind, Date at) {
        List<TVehRunLine> effective = new ArrayList<TVehRunLine>();
        if (!isKnownMeasure(measureKind) || at == null) {
            return effective;
        }
        List<TVehRunLine> all = this.vehRunLineMapper.selectList(
                new QueryWrapper<TVehRunLine>().eq("del_flag", 0));
        for (TVehRunLine r : all) {
            if (!measureKind.equals(r.getMeasureKind())) {
                continue;
            }
            // 已停用的不参与现行比对：册里翻得着、屏上列得出，只是不许捞它去比数
            if (r.getStatus() != null && r.getStatus() == 1) {
                continue;
            }
            // 分界数缺一格、后一格没越过前头那格，这条界这条线就站不住，不参与
            if (thresholdDefect(r) != null) {
                continue;
            }
            if (inOffice(r, at)) {
                effective.add(r);
            }
        }
        effective.sort(SPEAK_ORDER);
        return effective;
    }

    @Override
    public List<TVehRunLine> browseRegister(String measureKind) {
        QueryWrapper<TVehRunLine> wrapper = new QueryWrapper<TVehRunLine>().eq("del_flag", 0);
        if (isKnownMeasure(measureKind)) {
            wrapper.eq("measure_kind", measureKind);
        }
        // 翻检用：已停用、已交班的旧界一并摆得出；这条路只许看，不许捞去比数
        wrapper.orderByAsc("measure_kind").orderByDesc("eff_start").orderByDesc("id");
        return this.vehRunLineMapper.selectList(wrapper);
    }

    @Override
    public List<RunLineDefect> inspectRegister(String measureKind) {
        List<RunLineDefect> defects = new ArrayList<RunLineDefect>();
        List<TVehRunLine> all = this.vehRunLineMapper.selectList(
                new QueryWrapper<TVehRunLine>().eq("del_flag", 0)
                        .orderByAsc("measure_kind").orderByAsc("id"));
        for (TVehRunLine r : all) {
            if (isKnownMeasure(measureKind) && !measureKind.equals(r.getMeasureKind())) {
                continue;
            }
            // 不能只讲"数值不对劲"：哪一格空着、哪一格没越过前头那一格，指名道姓
            String detail = thresholdDefect(r);
            if (detail != null) {
                String kind = detail.startsWith("分界数") ? "threshold_order" : "threshold_missing";
                defects.add(new RunLineDefect(r.getRuleCode(), r.getRuleName(), kind, detail));
            }
        }
        return defects;
    }

    @Override
    public List<RunLineDefect> danglingScreenCodes(String measureKind, java.util.Collection<String> screenCodes,
                                                   Date at) {
        List<RunLineDefect> dangling = new ArrayList<RunLineDefect>();
        boolean byMeasure = isKnownMeasure(measureKind);
        if (at == null || screenCodes == null || screenCodes.isEmpty()) {
            return dangling;
        }
        Set<String> codes = new LinkedHashSet<String>();
        for (String c : screenCodes) {
            if (c != null && !c.trim().isEmpty()) {
                codes.add(c.trim());
            }
        }
        if (codes.isEmpty()) {
            return dangling;
        }
        // 含留痕删除一起捞，才说得清是"没这号"还是"删了/停了/交班了"
        List<TVehRunLine> all = this.vehRunLineMapper.selectList(
                new QueryWrapper<TVehRunLine>().in("rule_code", codes));
        java.util.Map<String, List<TVehRunLine>> byCode = new java.util.HashMap<String, List<TVehRunLine>>();
        for (TVehRunLine r : all) {
            List<TVehRunLine> bucket = byCode.get(r.getRuleCode());
            if (bucket == null) {
                bucket = new ArrayList<TVehRunLine>();
                byCode.put(r.getRuleCode(), bucket);
            }
            bucket.add(r);
        }
        for (String code : codes) {
            List<TVehRunLine> rows = byCode.get(code);
            if (rows == null || rows.isEmpty()) {
                dangling.add(new RunLineDefect(code, null, "screen_dangling",
                        "屏上挂着这个界次代号，界册里却翻不着"));
                continue;
            }
            if (hasEffective(rows, byMeasure ? measureKind : null, at)) {
                continue;
            }
            dangling.add(new RunLineDefect(code, rows.get(0).getRuleName(), "screen_dangling",
                    "屏上挂着这个界次代号，问话那一刻却请不出在位界："
                            + whyNotEffective(rows, byMeasure ? measureKind : null, at)));
        }
        return dangling;
    }

    // ------------------------------------------------------------------
    // 内里那一套走法（私有，外面摸不着，也不许另抄一份）
    // ------------------------------------------------------------------

    private boolean isKnownMeasure(String measureKind) {
        return MEASURE_SPEED.equals(measureKind) || MEASURE_DURATION.equals(measureKind);
    }

    /** 折算只往界册那一头折；车速与时长各走各的折法，舍法共用一把 HALF_UP */
    private BigDecimal convertToRegister(String measureKind, BigDecimal rawValue) {
        if (MEASURE_SPEED.equals(measureKind)) {
            return rawValue.multiply(SPEED_FACTOR).setScale(REGISTER_SCALE, SHARED_ROUNDING);
        }
        return rawValue.divide(MINUTES_PER_HOUR, REGISTER_SCALE, SHARED_ROUNDING);
    }

    /**
     * 在位口径只有一套：起用那一日已经够着（含当日）、交班那一日还没到（不含当日）。
     * 交班栏空着的存量手抄界认作一直在位。补判旧账传旧账自己的那一刻，
     * 与眼下最新落的那条不相干。
     */
    private boolean inOffice(TVehRunLine r, Date at) {
        Date start = r.getEffStart();
        if (start == null || at.before(start)) {
            return false;
        }
        Date end = r.getEffEnd();
        return end == null || at.before(end);
    }

    private boolean hasEffective(List<TVehRunLine> rows, String measureKind, Date at) {
        for (TVehRunLine r : rows) {
            Integer del = r.getDelFlag();
            if (del != null && del != 0) {
                continue;
            }
            if (measureKind != null && !measureKind.equals(r.getMeasureKind())) {
                continue;
            }
            if (r.getStatus() != null && r.getStatus() == 1) {
                continue;
            }
            if (thresholdDefect(r) != null) {
                continue;
            }
            if (inOffice(r, at)) {
                return true;
            }
        }
        return false;
    }

    /** 把"为什么请不出在位界"逐条落成一句句人话，便于屏上一条条指出 */
    private String whyNotEffective(List<TVehRunLine> rows, String measureKind, Date at) {
        List<String> reasons = new ArrayList<String>();
        for (TVehRunLine r : rows) {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(r.getRuleCode()).append("]");
            if (r.getDelFlag() != null && r.getDelFlag() != 0) {
                sb.append("已留痕删除");
            } else if (measureKind != null && !measureKind.equals(r.getMeasureKind())) {
                sb.append("是另一条线(").append(r.getMeasureKind()).append(")的界，请不到这条线上来");
            } else if (r.getStatus() != null && r.getStatus() == 1) {
                sb.append("已停用，不参与现行比对");
            } else {
                String bad = thresholdDefect(r);
                if (bad != null) {
                    sb.append(bad);
                } else if (r.getEffStart() == null) {
                    sb.append("起用那一日空着，请不出在位");
                } else if (at.before(r.getEffStart())) {
                    sb.append("起用那一日(").append(fmt(r.getEffStart())).append(")还没够着");
                } else if (r.getEffEnd() != null && !at.before(r.getEffEnd())) {
                    sb.append("交班那一日(").append(fmt(r.getEffEnd())).append(")已到，旧界管到该日之前");
                }
            }
            if (sb.charAt(sb.length() - 1) != ']') {
                reasons.add(sb.toString());
            }
        }
        return reasons.isEmpty() ? "没有一条站得住" : join(reasons);
    }

    /**
     * 三个分界数体检：缺一格指名哪一格空着；后一格不大于前一格指名哪一格没越过前头那一格。
     * 没毛病返回 null。
     */
    private String thresholdDefect(TVehRunLine r) {
        if (r.getTh1Max() == null) {
            return "起算数值(th1_max)那一格空着，这条界这条线站不住";
        }
        if (r.getTh2Max() == null) {
            return "够线数值(th2_max)那一格空着，这条界这条线站不住";
        }
        if (r.getTh3Max() == null) {
            return "封顶数值(th3_max)那一格空着，这条界这条线站不住";
        }
        if (r.getTh2Max().compareTo(r.getTh1Max()) <= 0) {
            return "分界数次序不对：够线数值(th2_max)没越过前头的起算数值(th1_max)，这条界这条线站不住";
        }
        if (r.getTh3Max().compareTo(r.getTh2Max()) <= 0) {
            return "分界数次序不对：封顶数值(th3_max)没越过前头的够线数值(th2_max)，这条界这条线站不住";
        }
        return null;
    }

    private String levelName(int level) {
        if (level == LEVEL_TIP) {
            return "提示";
        }
        if (level == LEVEL_STRICT) {
            return "严管";
        }
        return "立案";
    }

    private String fmt(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    private String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append("；");
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
