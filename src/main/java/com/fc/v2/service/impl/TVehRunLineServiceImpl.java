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
import com.fc.v2.mapper.auto.TVehRunLineVerdictRecordMapper;
import com.fc.v2.model.auto.TVehRunLine;
import com.fc.v2.model.auto.TVehRunLineVerdictRecord;
import com.fc.v2.model.custom.RunLineDefect;
import com.fc.v2.model.custom.RunLineVerdict;
import com.fc.v2.service.ITVehRunLineService;

/**
 * 运行界次判定线 Service业务层处理。
 *
 * 界次归层四问只由本类 verdict 一处作答：折算口径、在位口径、说话次序、三层归层都在这里，
 * 屏上不许另摆第二套；在位名单（listEffective/listAvailable）与裁决请界走同一处筛选，两本账不许有。
 *
 * 旧办法（evaluate/evaluateTop/listAvailable/usable/countAvailable）名目、参数表照原样摆着，
 * 但判档不再另抄一套：请界、在位、量程、压线归层全转新走法那同一套口径，
 * 旧路只留它自己的取数习惯（输入按界册单位直收、不折算），等号两边从此只许一个说法。
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

    /** 车速量程顶：车上报每秒多少米，过了这个数就是探头坏了，不许塞进档里（360 km/h） */
    private static final BigDecimal SPEED_RAW_MAX = new BigDecimal("100");
    /** 连续驾驶时长量程顶：一次连续驾驶最多收 1440 分钟（24 小时），再多按越界办 */
    private static final BigDecimal DURATION_RAW_MAX = new BigDecimal("1440");

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

    /** 判完即归档在册：每回裁决连同说话界的分界数快照落这一册，追溯只认归档的这一份 */
    @javax.annotation.Resource
    private TVehRunLineVerdictRecordMapper verdictRecordMapper;

    // ------------------------------------------------------------------
    // 旧办法（名目照旧）：请界、在位、量程、归层全转同一套口径，不再各判各的
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
        // 请界不再只挑"最新一条"：补判旧账传旧账那一刻，就只请那一刻在位的那条，
        // 已停用、已交班、分界数站不住的同新走法一样请不出
        List<TVehRunLine> sameCode = selectRegister();
        List<TVehRunLine> effective = new ArrayList<TVehRunLine>();
        for (TVehRunLine r : sameCode) {
            if (ruleCode.equals(r.getRuleCode()) && isEffective(r, null, at)) {
                effective.add(r);
            }
        }
        if (effective.isEmpty()) {
            return 0;
        }
        effective.sort(SPEAK_ORDER);
        TVehRunLine speaker = effective.get(0);
        // 旧路输入已按界册单位收，不再折算；量程与压线归层仍走同一套
        if (!inRegisterRange(speaker.getMeasureKind(), input)) {
            return 0;
        }
        Integer level = grade(speaker, input);
        return level == null ? 0 : level.intValue();
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return 0;
        }
        // 与裁决请界同出一处：停用/交班/未起用/站不住的一律不在名单上，次序也照说话序排定，
        // 不许不排序就捞头一条（同优先级时两次给不同档位就这么来的）
        List<TVehRunLine> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return 0;
        }
        TVehRunLine speaker = avail.get(0);
        if (!inRegisterRange(speaker.getMeasureKind(), input)) {
            return 0;
        }
        Integer level = grade(speaker, input);
        return level == null ? 0 : level.intValue();
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TVehRunLine r = this.vehRunLineMapper.selectById(id);
        // 与在位名单同一处筛选：停用、交班、未起用、分界数站不住的都不算可用
        return r != null && isEffective(r, null, at);
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        return listAvailable(at).size();
    }

    /** 旧名目：口径已并到新走法同一处（只排除留痕删除那套旧说法作废，停用界不许再混进来） */
    @Override
    public List<TVehRunLine> listAvailable(Date at) {
        List<TVehRunLine> available = new ArrayList<TVehRunLine>();
        if (at == null) {
            return available;
        }
        for (TVehRunLine r : selectRegister()) {
            if (isEffective(r, null, at)) {
                available.add(r);
            }
        }
        available.sort(SPEAK_ORDER);
        return available;
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

        // 量程先过：越界值（探头念飞了那种）判出档位，给兜底结论，不许塞进立案层充数
        if (!inRawRange(measureKind, rawValue)) {
            RunLineVerdict out = new RunLineVerdict(TEXT_OUT_OF_RANGE, null, null, null, converted);
            archive(measureKind, rawValue, converted, at, null, out);
            return out;
        }

        // 2)+3) 请界与说话：在位名单与屏上同出一处，排定了头一个就是说话的
        List<TVehRunLine> effective = listEffective(measureKind, at);
        if (effective.isEmpty()) {
            // 四条界一条都够不上：不给层，代号与起用日两栏一并留空，不许抄旁边那条
            RunLineVerdict noRule = new RunLineVerdict(TEXT_NO_RULE, null, null, null, converted);
            archive(measureKind, rawValue, converted, at, null, noRule);
            return noRule;
        }
        TVehRunLine speaker = effective.get(0);

        // 4) 归层只有一套说法，压线归本层：
        //    [起算,够线)提示、[够线,封顶)严管、[封顶及以上)立案；没够着起算的不给层
        Integer level = grade(speaker, converted);
        RunLineVerdict result;
        if (level == null) {
            result = new RunLineVerdict(TEXT_BELOW_START, null,
                    speaker.getRuleCode(), speaker.getEffStart(), converted);
        } else {
            result = new RunLineVerdict(levelName(level.intValue()), level,
                    speaker.getRuleCode(), speaker.getEffStart(), converted);
        }
        archive(measureKind, rawValue, converted, at, speaker, result);
        return result;
    }

    @Override
    public List<TVehRunLine> listEffective(String measureKind, Date at) {
        List<TVehRunLine> effective = new ArrayList<TVehRunLine>();
        if (!isKnownMeasure(measureKind) || at == null) {
            return effective;
        }
        for (TVehRunLine r : selectRegister()) {
            if (isEffective(r, measureKind, at)) {
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

    @Override
    public List<TVehRunLineVerdictRecord> listVerdictArchive(String measureKind, Date from, Date to,
                                                             String ruleCode) {
        QueryWrapper<TVehRunLineVerdictRecord> wrapper =
                new QueryWrapper<TVehRunLineVerdictRecord>().eq("del_flag", 0);
        if (isKnownMeasure(measureKind)) {
            wrapper.eq("measure_kind", measureKind);
        }
        if (from != null) {
            wrapper.ge("ask_at", from);
        }
        if (to != null) {
            wrapper.le("ask_at", to);
        }
        if (ruleCode != null && !ruleCode.trim().isEmpty()) {
            wrapper.eq("rule_code", ruleCode.trim());
        }
        // 追溯翻的是归档在册那一份：按问话时刻倒序，同刻再按归档先后
        wrapper.orderByDesc("ask_at").orderByDesc("id");
        return this.verdictRecordMapper.selectList(wrapper);
    }

    // ------------------------------------------------------------------
    // 内里那一套走法（私有，外面摸不着，也不许另抄一份）
    // ------------------------------------------------------------------

    /** 界册在册行（只排除留痕删除），各路口径都从这一册子上筛，不许多查一遍 */
    private List<TVehRunLine> selectRegister() {
        return this.vehRunLineMapper.selectList(new QueryWrapper<TVehRunLine>().eq("del_flag", 0));
    }

    /**
     * 在位口径只此一处，裁决请界、屏上名单、旧办法可用性、屏挂核对全走它：
     * 所量类别对得上（传 null 不分类别）、未停用、分界数站得住、起用含当日/交班不含当日。
     * 交班栏空着的存量手抄界认作一直在位——存量兼容也只许这一种说法。
     */
    private boolean isEffective(TVehRunLine r, String measureKind, Date at) {
        if (measureKind != null && !measureKind.equals(r.getMeasureKind())) {
            return false;
        }
        // 已停用的不参与现行比对：册里翻得着、屏上列得出，只是不许捞它去比数
        if (r.getStatus() != null && r.getStatus() == 1) {
            return false;
        }
        // 分界数缺一格、后一格没越过前头那格，这条界这条线就站不住，不参与
        if (thresholdDefect(r) != null) {
            return false;
        }
        return inOffice(r, at);
    }

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

    /** 车上报数的量程：按车上端口径把门关住，越界的数判出档位（负值也在此挡住） */
    private boolean inRawRange(String measureKind, BigDecimal rawValue) {
        if (rawValue.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        BigDecimal max = MEASURE_SPEED.equals(measureKind) ? SPEED_RAW_MAX : DURATION_RAW_MAX;
        return rawValue.compareTo(max) <= 0;
    }

    /** 旧路收的已是界册单位，量程门按界册这一头同一把刻度比 */
    private boolean inRegisterRange(String measureKind, BigDecimal registerValue) {
        if (registerValue.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        if (MEASURE_SPEED.equals(measureKind)) {
            return registerValue.compareTo(SPEED_RAW_MAX.multiply(SPEED_FACTOR)) <= 0;
        }
        if (MEASURE_DURATION.equals(measureKind)) {
            return registerValue.compareTo(DURATION_RAW_MAX.divide(MINUTES_PER_HOUR,
                    REGISTER_SCALE, RoundingMode.CEILING)) <= 0;
        }
        // 类别念不出的界，量程门不替它担保
        return false;
    }

    /**
     * 三层归层只有一套说法，新旧两路共用：
     * [起算,够线)提示、[够线,封顶)严管、[封顶及以上)立案；没够着起算返回 null（不给层）。
     */
    private Integer grade(TVehRunLine r, BigDecimal registerValue) {
        if (registerValue.compareTo(r.getTh1Max()) < 0) {
            return null;
        }
        if (registerValue.compareTo(r.getTh2Max()) < 0) {
            return Integer.valueOf(LEVEL_TIP);
        }
        if (registerValue.compareTo(r.getTh3Max()) < 0) {
            return Integer.valueOf(LEVEL_STRICT);
        }
        return Integer.valueOf(LEVEL_CASE);
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
            if (isEffective(r, measureKind, at)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判完即归档在册：说话的那条界连同分界数、起用日、交班次序照裁决那一刻快照下来，
     * 日后界册改了、界交班了，追溯只以这一份为准，不许拿最新版阈值把旧账重算成另一个档。
     * 无可用界/未到起算/超量程的也归档，代号与分界数各栏留空。
     */
    private void archive(String measureKind, BigDecimal rawValue, BigDecimal converted, Date at,
                         TVehRunLine speaker, RunLineVerdict result) {
        if (this.verdictRecordMapper == null) {
            return;
        }
        TVehRunLineVerdictRecord rec = new TVehRunLineVerdictRecord();
        rec.setMeasureKind(measureKind);
        rec.setRawValue(rawValue);
        rec.setConvertedValue(converted);
        rec.setAskAt(at);
        rec.setLevel(result.getLevel());
        rec.setLevelText(result.getLevelText());
        rec.setArchivedAt(new Date());
        rec.setDelFlag(0);
        if (speaker != null) {
            rec.setRuleCode(speaker.getRuleCode());
            rec.setRuleName(speaker.getRuleName());
            rec.setEffStart(speaker.getEffStart());
            rec.setTh1Max(speaker.getTh1Max());
            rec.setTh2Max(speaker.getTh2Max());
            rec.setTh3Max(speaker.getTh3Max());
            rec.setUnitKind(speaker.getUnitKind());
            rec.setPriority(speaker.getPriority());
        }
        this.verdictRecordMapper.insert(rec);
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
