package com.fc.v2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TVehRunLineMapper;
import com.fc.v2.mapper.auto.TVehRunVerdictMapper;
import com.fc.v2.model.auto.TVehRunLine;
import com.fc.v2.model.auto.TVehRunVerdict;
import com.fc.v2.model.custom.RunLineDefect;
import com.fc.v2.model.custom.RunLineVerdict;
import com.fc.v2.service.impl.TVehRunLineServiceImpl;

/**
 * 界次归层口径测试（不启 Spring，桩掉数据层）。
 * 把需求里点过名的坑一个个钉住：折算方向、同一把舍法、压线一套说法、
 * 历史时刻不许新界顶替、交班次序/界次代序、无可用界两栏留空、停用不参与、
 * 存量空交班、分界数缺格/错序指名、屏挂悬空代号指出、量程越界不判档、
 * 判完即归档、旧名目与新走法同一把尺。
 */
@ExtendWith(MockitoExtension.class)
public class TVehRunLineServiceImplTest {

    @Mock
    private TVehRunLineMapper vehRunLineMapper;

    @Mock
    private TVehRunVerdictMapper vehRunVerdictMapper;

    @InjectMocks
    private TVehRunLineServiceImpl service;

    private static Date day(String s) {
        try {
            return new SimpleDateFormat(s.length() <= 10 ? "yyyy-MM-dd" : "yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 造一条界：阈值单位在界册那一头 */
    private static TVehRunLine rule(String code, String measure, String th1, String th2, String th3,
                                   String start, String end, Integer priority, Integer status) {
        TVehRunLine r = new TVehRunLine();
        r.setRuleCode(code);
        r.setRuleName("界" + code);
        r.setMeasureKind(measure);
        r.setTh1Max(new BigDecimal(th1));
        r.setTh2Max(new BigDecimal(th2));
        r.setTh3Max(new BigDecimal(th3));
        r.setEffStart(day(start));
        r.setEffEnd(end == null ? null : day(end));
        r.setPriority(priority);
        r.setStatus(status == null ? 0 : status);
        r.setDelFlag(0);
        return r;
    }

    private void stubRegister(TVehRunLine... rules) {
        when(vehRunLineMapper.selectList(any())).thenReturn(new ArrayList<TVehRunLine>(Arrays.asList(rules)));
    }

    // ---- 一、折单位：只往界册那一头折，三个分界同一把舍法 ----

    @Test
    public void speedConvertsMetersPerSecondToKilometersPerHour() {
        // 60/100/120 km/h；25 m/s = 90.000 km/h → 提示
        stubRegister(rule("S1", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                "2020-01-01", null, 0, 0));
        RunLineVerdict v = service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("25"),
                day("2026-10-04"));
        assertEquals("提示", v.getLevelText());
        assertEquals(1, v.getLevel());
        assertEquals("S1", v.getRuleCode());
        assertEquals(0, new BigDecimal("90.000").compareTo(v.getConverted()));
        assertEquals(3, v.getConverted().scale());
    }

    @Test
    public void durationConvertsMinutesToHours() {
        // 4/8/12 小时；300 分钟 = 5.000 小时 → 提示
        stubRegister(rule("D1", ITVehRunLineService.MEASURE_DURATION, "4", "8", "12",
                "2020-01-01", null, 0, 0));
        RunLineVerdict v = service.verdict(ITVehRunLineService.MEASURE_DURATION, new BigDecimal("300"),
                day("2026-10-04"));
        assertEquals("提示", v.getLevelText());
        assertEquals(0, new BigDecimal("5.000").compareTo(v.getConverted()));
    }

    @Test
    public void singleRoundingHalfUpMovesAcrossBoundary() {
        // 去年冬季那条压线报警：一处四舍五入一处截尾差一层。
        // 16.66656 m/s ×3.6 = 59.999616 → HALF_UP 60.000（压起算线→提示），截尾会是 59.999（未到起算）
        stubRegister(rule("S1", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                "2020-01-01", null, 0, 0));
        RunLineVerdict v = service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("16.66656"),
                day("2026-10-04"));
        assertEquals("提示", v.getLevelText());
        assertEquals(1, v.getLevel());
        assertEquals(0, new BigDecimal("60.000").compareTo(v.getConverted()));
    }

    // ---- 压线只有一套说法：[起算,够线)提示 [够线,封顶)严管 [封顶及以上)立案 ----

    @Test
    public void boundaryOnThresholdHasSingleAnswer() {
        stubRegister(rule("D1", ITVehRunLineService.MEASURE_DURATION, "4", "8", "12",
                "2020-01-01", null, 0, 0));
        // 240 分钟 = 4.000 小时，正压起算 → 归本层（提示）
        assertEquals("提示", service.verdict(ITVehRunLineService.MEASURE_DURATION,
                new BigDecimal("240"), day("2026-10-04")).getLevelText());
        // 480 分钟 = 8.000 小时，正压够线 → 严管
        assertEquals("严管", service.verdict(ITVehRunLineService.MEASURE_DURATION,
                new BigDecimal("480"), day("2026-10-04")).getLevelText());
        // 720 分钟 = 12.000 小时，正压封顶 → 立案
        RunLineVerdict onCap = service.verdict(ITVehRunLineService.MEASURE_DURATION,
                new BigDecimal("720"), day("2026-10-04"));
        assertEquals("立案", onCap.getLevelText());
        assertEquals(3, onCap.getLevel());
        // 239 分钟 = 3.983 小时，还没够着起算
        RunLineVerdict below = service.verdict(ITVehRunLineService.MEASURE_DURATION,
                new BigDecimal("239"), day("2026-10-04"));
        assertEquals("未到起算", below.getLevelText());
        assertNull(below.getLevel());
    }

    @Test
    public void sameMomentAskedTwiceGivesSameAnswer() {
        stubRegister(rule("D1", ITVehRunLineService.MEASURE_DURATION, "4", "8", "12",
                "2020-01-01", null, 0, 0));
        RunLineVerdict a = service.verdict(ITVehRunLineService.MEASURE_DURATION,
                new BigDecimal("480"), day("2026-10-04 09:00:00"));
        RunLineVerdict b = service.verdict(ITVehRunLineService.MEASURE_DURATION,
                new BigDecimal("480"), day("2026-10-04 09:00:00"));
        assertEquals(a.getLevelText(), b.getLevelText());
        assertEquals(a.getRuleCode(), b.getRuleCode());
    }

    // ---- 二、问话的那一刻：补判旧账用旧账自己的界，不许新界顶替 ----

    @Test
    public void historicalMomentUsesHistoricalRuleNotNewOne() {
        // 旧界管 2024 全年，阈值高（90→提示）；新界 2026 才起，阈值低（90→立案）
        stubRegister(
                rule("OLD", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                        "2024-01-01", "2025-01-01", 0, 0),
                rule("NEW", ITVehRunLineService.MEASURE_SPEED, "10", "20", "30",
                        "2026-01-01", null, 0, 0));
        // 前年秋天的报警问前年秋天的那一刻 → 旧界说话，档位高一格才对
        RunLineVerdict old = service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("25"),
                day("2024-09-15"));
        assertEquals("OLD", old.getRuleCode());
        assertEquals("提示", old.getLevelText());
        // 今年再问 → 新界说话
        RunLineVerdict now = service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("25"),
                day("2026-10-04"));
        assertEquals("NEW", now.getRuleCode());
        assertEquals("立案", now.getLevelText());
    }

    @Test
    public void handoverDayHasSingleSemanticsStartInclusiveEndExclusive() {
        stubRegister(
                rule("OLD", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                        "2026-01-01", "2026-02-01", 0, 0),
                rule("NEW", ITVehRunLineService.MEASURE_SPEED, "10", "20", "30",
                        "2026-02-01", null, 0, 0));
        // 起用那一日已经够着：1月1日旧界在位
        assertEquals("OLD", service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("25"),
                day("2026-01-01 00:00:00")).getRuleCode());
        assertEquals("OLD", service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("25"),
                day("2026-01-31 23:59:59")).getRuleCode());
        // 交班那一日还没到才算在位：2月1日零点起旧界管不着，新界接话
        assertEquals("NEW", service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("25"),
                day("2026-02-01 00:00:00")).getRuleCode());
    }

    @Test
    public void gapBetweenRulesGivesNoAvailableRule() {
        stubRegister(
                rule("OLD", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                        "2024-01-01", "2025-01-01", 0, 0),
                rule("NEW", ITVehRunLineService.MEASURE_SPEED, "10", "20", "30",
                        "2026-01-01", null, 0, 0));
        RunLineVerdict v = service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("25"),
                day("2025-06-01"));
        // 四条界一条都够不上：结论写无可用界，代号与起用日两栏一并留空
        assertEquals(ITVehRunLineService.TEXT_NO_RULE, v.getLevelText());
        assertNull(v.getLevel());
        assertNull(v.getRuleCode());
        assertNull(v.getEffStart());
        assertNotNull(v.getConverted());
    }

    // ---- 三、在位凑两条：先比交班次序，相同再比界次代序末位 ----

    @Test
    public void higherPrioritySpeaksFirst() {
        stubRegister(
                rule("Z01", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                        "2020-01-01", null, 2, 0),
                rule("A99", ITVehRunLineService.MEASURE_SPEED, "10", "20", "30",
                        "2020-01-01", null, 1, 0));
        // 90 km/h：Z01 判提示、A99 判立案；交班次序 2 > 1，Z01 说话
        RunLineVerdict v = service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("25"),
                day("2026-10-04"));
        assertEquals("Z01", v.getRuleCode());
        assertEquals("提示", v.getLevelText());
    }

    @Test
    public void samePriorityLastCodeInLexicographicOrderWins() {
        stubRegister(
                rule("R10", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                        "2020-01-01", null, 2, 0),
                rule("R2", ITVehRunLineService.MEASURE_SPEED, "10", "20", "30",
                        "2020-01-01", null, 2, 0));
        // 次序相同：照排法落在末位——字典序 "R2" 大于 "R10"，R2 说话，90 km/h 落在它的立案层
        RunLineVerdict v = service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("25"),
                day("2026-10-04"));
        assertEquals("R2", v.getRuleCode());
        assertEquals("立案", v.getLevelText());
        // 名单次序与说话的是同一条
        List<TVehRunLine> effective = service.listEffective(ITVehRunLineService.MEASURE_SPEED,
                day("2026-10-04"));
        assertEquals("R2", effective.get(0).getRuleCode());
    }

    @Test
    public void disabledRuleDoesNotJoinCurrentEvaluation() {
        stubRegister(rule("OFF", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                "2020-01-01", null, 0, 1));
        RunLineVerdict v = service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("25"),
                day("2026-10-04"));
        assertEquals(ITVehRunLineService.TEXT_NO_RULE, v.getLevelText());
        assertNull(v.getRuleCode());
        // 翻检道照旧翻得着，只是不许捞去比数
        assertEquals(1, service.browseRegister(ITVehRunLineService.MEASURE_SPEED).size());
        assertEquals(0, service.listEffective(ITVehRunLineService.MEASURE_SPEED,
                day("2026-10-04")).size());
    }

    @Test
    public void otherMeasureRulesNeverSpeak() {
        // 册里只有时长界，问车速 → 无可用界，不许拿别条线凑
        stubRegister(rule("D1", ITVehRunLineService.MEASURE_DURATION, "4", "8", "12",
                "2020-01-01", null, 0, 0));
        RunLineVerdict v = service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("25"),
                day("2026-10-04"));
        assertEquals(ITVehRunLineService.TEXT_NO_RULE, v.getLevelText());
    }

    // ---- 四、存量那道口子：交班栏空着认作一直在位 ----

    @Test
    public void legacyRuleWithBlankHandoverStaysInOffice() {
        stubRegister(rule("LEG", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                "2000-01-01", null, 0, 0));
        RunLineVerdict v = service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("25"),
                day("2026-10-04"));
        assertEquals("LEG", v.getRuleCode());
        assertEquals("提示", v.getLevelText());
    }

    // ---- 分界数缺一个 / 后一个不大于前一个：站不住，不参与，且指名道姓 ----

    @Test
    public void missingThresholdExcludesRuleAndNamesCell() {
        TVehRunLine bad = rule("BAD", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                "2020-01-01", null, 0, 0);
        bad.setTh2Max(null);
        stubRegister(bad);
        RunLineVerdict v = service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("25"),
                day("2026-10-04"));
        assertEquals(ITVehRunLineService.TEXT_NO_RULE, v.getLevelText());
        List<RunLineDefect> defects = service.inspectRegister(ITVehRunLineService.MEASURE_SPEED);
        assertEquals(1, defects.size());
        assertEquals("threshold_missing", defects.get(0).getKind());
        assertTrue(defects.get(0).getDetail().contains("th2_max"), "要指名哪一格空着");
    }

    @Test
    public void nonIncreasingThresholdsExcludeRuleAndNamePair() {
        TVehRunLine bad = rule("BAD", ITVehRunLineService.MEASURE_SPEED, "60", "60", "120",
                "2020-01-01", null, 0, 0);
        TVehRunLine good = rule("OK", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                "2020-01-01", null, 0, 0);
        stubRegister(bad, good);
        // 站不住的界不参与，好界照旧说话
        RunLineVerdict v = service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("25"),
                day("2026-10-04"));
        assertEquals("OK", v.getRuleCode());
        List<RunLineDefect> defects = service.inspectRegister(ITVehRunLineService.MEASURE_SPEED);
        assertEquals(1, defects.size());
        assertEquals("threshold_order", defects.get(0).getKind());
        assertTrue(defects.get(0).getDetail().contains("th2_max"));
        assertTrue(defects.get(0).getDetail().contains("th1_max"), "要指名没越过前头哪一格");
    }

    // ---- 屏上挂代号、册里那一刻请不出在位界：一条条指出 ----

    @Test
    public void danglingScreenCodesArePointedOutOneByOne() {
        TVehRunLine disabled = rule("OFF", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                "2020-01-01", null, 0, 1);
        TVehRunLine future = rule("FUT", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                "2027-01-01", null, 0, 0);
        TVehRunLine good = rule("GOOD", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                "2020-01-01", null, 0, 0);
        when(vehRunLineMapper.selectList(any()))
                .thenReturn(new ArrayList<TVehRunLine>(Arrays.asList(disabled, future, good)));
        List<RunLineDefect> dangling = service.danglingScreenCodes(ITVehRunLineService.MEASURE_SPEED,
                Arrays.asList("GHOST", "OFF", "FUT", "GOOD"), day("2026-10-04"));
        assertEquals(3, dangling.size());
        assertEquals("GHOST", dangling.get(0).getRuleCode());
        assertTrue(dangling.get(0).getDetail().contains("翻不着"));
        assertEquals("OFF", dangling.get(1).getRuleCode());
        assertTrue(dangling.get(1).getDetail().contains("停用"));
        assertEquals("FUT", dangling.get(2).getRuleCode());
        assertTrue(dangling.get(2).getDetail().contains("还没够着"));
    }

    @Test
    public void danglingCheckIsScopedToMeasureLine() {
        // 时长界挂在车速屏上：跨线请不到，照样算悬空
        TVehRunLine durationRule = rule("D9", ITVehRunLineService.MEASURE_DURATION, "4", "8", "12",
                "2020-01-01", null, 0, 0);
        when(vehRunLineMapper.selectList(any()))
                .thenReturn(new ArrayList<TVehRunLine>(Arrays.asList(durationRule)));
        List<RunLineDefect> onSpeedScreen = service.danglingScreenCodes(ITVehRunLineService.MEASURE_SPEED,
                Arrays.asList("D9"), day("2026-10-04"));
        assertEquals(1, onSpeedScreen.size());
        assertTrue(onSpeedScreen.get(0).getDetail().contains("另一条线"));
        // 不分类别点（measureKind=null）则请得出，不算悬空
        List<RunLineDefect> acrossLines = service.danglingScreenCodes(null,
                Arrays.asList("D9"), day("2026-10-04"));
        assertEquals(0, acrossLines.size());
    }

    // ---- 缺参 ----

    @Test
    public void missingArgumentsYieldNullVerdict() {
        assertNull(service.verdict(null, new BigDecimal("25"), day("2026-10-04")));
        assertNull(service.verdict(ITVehRunLineService.MEASURE_SPEED, null, day("2026-10-04")));
        assertNull(service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("25"), null));
        assertNull(service.verdict("bogus", new BigDecimal("25"), day("2026-10-04")));
    }

    // ---- 五、量程校验只此一处：越界的数不判档 ----

    @Test
    public void outOfMeasureRangeYieldsNullVerdict() {
        stubRegister(rule("S1", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                "2020-01-01", null, 0, 0));
        // 负数、车速过 70 m/s、时长过 1440 分钟：越出量程不判档，与缺参同一路回绝
        assertNull(service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("-0.1"),
                day("2026-10-04")));
        assertNull(service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("70.001"),
                day("2026-10-04")));
        assertNull(service.verdict(ITVehRunLineService.MEASURE_DURATION, new BigDecimal("1441"),
                day("2026-10-04")));
        // 贴着量程顶的照样判：70 m/s = 252.000 km/h → 立案
        RunLineVerdict atCap = service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("70"),
                day("2026-10-04"));
        assertEquals("立案", atCap.getLevelText());
    }

    // ---- 六、判完即归档在册：日后追溯以这一份为准 ----

    @Test
    public void verdictIsArchivedAsJudged() {
        stubRegister(rule("S1", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                "2020-01-01", null, 0, 0));
        Date moment = day("2026-10-04 09:30:00");
        RunLineVerdict v = service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("25"),
                moment);
        assertEquals("提示", v.getLevelText());
        ArgumentCaptor<TVehRunVerdict> cap = ArgumentCaptor.forClass(TVehRunVerdict.class);
        verify(vehRunVerdictMapper).insert(cap.capture());
        TVehRunVerdict row = cap.getValue();
        assertEquals(ITVehRunLineService.MEASURE_SPEED, row.getMeasureKind());
        assertEquals(0, new BigDecimal("25").compareTo(row.getRawValue()));
        assertEquals(0, new BigDecimal("90.000").compareTo(row.getConverted()));
        assertEquals("提示", row.getLevelText());
        assertEquals(1, row.getLevel().intValue());
        assertEquals("S1", row.getRuleCode());
        assertEquals(moment, row.getAskedAt());
        assertNotNull(row.getEffStart());
    }

    @Test
    public void noRuleVerdictIsArchivedWithBlankRuleColumns() {
        stubRegister(rule("OFF", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                "2020-01-01", null, 0, 1));
        service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("25"), day("2026-10-04"));
        ArgumentCaptor<TVehRunVerdict> cap = ArgumentCaptor.forClass(TVehRunVerdict.class);
        verify(vehRunVerdictMapper).insert(cap.capture());
        // 无可用界也照落册：结论"无可用界"，代号与起用日两栏留空
        assertEquals(ITVehRunLineService.TEXT_NO_RULE, cap.getValue().getLevelText());
        assertNull(cap.getValue().getRuleCode());
        assertNull(cap.getValue().getEffStart());
    }

    @Test
    public void rejectedVerdictIsNotArchived() {
        // 缺参、越出量程：没判成就没有归档，不许落一份空结论
        assertNull(service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("-1"),
                day("2026-10-04")));
        assertNull(service.verdict(null, new BigDecimal("25"), day("2026-10-04")));
        verify(vehRunVerdictMapper, never()).insert(any());
    }

    // ---- 七、旧名目与新走法同一把尺（签名不动的老名目，内里不再另摆口径） ----

    @Test
    public void oldBoundarySameSideAsNewCaliber() {
        // 等号不分两家：老名目压线同样归高一档（老档序 1..4 对应 未到起算/提示/严管/立案）
        stubRegister(rule("S1", ITVehRunLineService.MEASURE_SPEED, "30", "60", "90",
                "2020-01-01", null, 0, 0));
        assertEquals(1, service.evaluate("S1", new BigDecimal("29.999"), day("2026-10-04")));
        assertEquals(2, service.evaluate("S1", new BigDecimal("30"), day("2026-10-04")));
        assertEquals(3, service.evaluate("S1", new BigDecimal("60"), day("2026-10-04")));
        assertEquals(4, service.evaluate("S1", new BigDecimal("90"), day("2026-10-04")));
    }

    @Test
    public void oldEvaluateUsesHistoricalRuleNotNewest() {
        // 同一代号两版界：历史时点重算用历史界，不许拿最新版顶替（报表与归档才对得齐）
        stubRegister(
                rule("S1", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                        "2024-01-01", "2025-01-01", 0, 0),
                rule("S1", ITVehRunLineService.MEASURE_SPEED, "10", "20", "30",
                        "2026-01-01", null, 0, 0));
        // 2024 年秋问 90：旧版界说话 → [60,100) 档 2
        assertEquals(2, service.evaluate("S1", new BigDecimal("90"), day("2024-09-15")));
        // 2026 年问 90：新版界说话 → ≥30 档 4
        assertEquals(4, service.evaluate("S1", new BigDecimal("90"), day("2026-10-04")));
    }

    @Test
    public void oldPathsExcludeDisabledAndDefective() {
        // 停用项混不进可用判据：老名目与新走法同一处筛选
        TVehRunLine off = rule("OFF", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                "2020-01-01", null, 0, 1);
        TVehRunLine bad = rule("BAD", ITVehRunLineService.MEASURE_SPEED, "60", "60", "120",
                "2020-01-01", null, 0, 0);
        stubRegister(off, bad);
        assertEquals(0, service.listAvailable(day("2026-10-04")).size());
        assertEquals(0, service.countAvailable(day("2026-10-04")));
        assertEquals(0, service.evaluate("OFF", new BigDecimal("90"), day("2026-10-04")));
        assertEquals(0, service.evaluate("BAD", new BigDecimal("90"), day("2026-10-04")));
        assertEquals(0, service.evaluateTop(new BigDecimal("90"), day("2026-10-04")));
    }

    @Test
    public void oldTopIsDeterministicAtSamePriority() {
        // 同优先级有取舍规则：按界次代号末位，两次问同一个答复
        stubRegister(
                rule("R10", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                        "2020-01-01", null, 2, 0),
                rule("R2", ITVehRunLineService.MEASURE_SPEED, "10", "20", "30",
                        "2020-01-01", null, 2, 0));
        assertEquals(4, service.evaluateTop(new BigDecimal("90"), day("2026-10-04")));
        assertEquals(4, service.evaluateTop(new BigDecimal("90"), day("2026-10-04")));
        assertEquals("R2", service.listAvailable(day("2026-10-04")).get(0).getRuleCode());
    }

    @Test
    public void oldRangeCheckSharedByBothEntries() {
        // 量程一处卡：evaluate 与 evaluateTop 同一把卡尺（老名目 0..100，界册那一头的数）
        stubRegister(rule("S1", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                "2020-01-01", null, 0, 0));
        assertEquals(0, service.evaluate("S1", new BigDecimal("-1"), day("2026-10-04")));
        assertEquals(0, service.evaluate("S1", new BigDecimal("101"), day("2026-10-04")));
        assertEquals(0, service.evaluateTop(new BigDecimal("-1"), day("2026-10-04")));
        assertEquals(0, service.evaluateTop(new BigDecimal("101"), day("2026-10-04")));
        // 贴着量程顶的照样判
        assertEquals(3, service.evaluate("S1", new BigDecimal("100"), day("2026-10-04")));
        assertEquals(3, service.evaluateTop(new BigDecimal("100"), day("2026-10-04")));
    }

    @Test
    public void usableFollowsSameCaliberAsEffectiveList() {
        TVehRunLine ok = rule("S1", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                "2020-01-01", null, 0, 0);
        ok.setId(1L);
        TVehRunLine off = rule("OFF", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                "2020-01-01", null, 0, 1);
        off.setId(2L);
        when(vehRunLineMapper.selectById(1L)).thenReturn(ok);
        when(vehRunLineMapper.selectById(2L)).thenReturn(off);
        assertTrue(service.usable(1L, day("2026-10-04")));
        // 还没起用的那一刻请不出在位
        assertFalse(service.usable(1L, day("2019-12-31")));
        // 已停用的不可用
        assertFalse(service.usable(2L, day("2026-10-04")));
        assertFalse(service.usable(99L, day("2026-10-04")));
        assertFalse(service.usable(null, day("2026-10-04")));
    }

    @Test
    public void legacyBlankHandoverStaysInOfficeOnOldPathsToo() {
        // 兼容存量两处一致：交班栏空着的存量界，老名目同样认作一直在位
        stubRegister(rule("LEG", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                "2000-01-01", null, 0, 0));
        assertEquals(1, service.listAvailable(day("2026-10-04")).size());
        assertEquals(2, service.evaluate("LEG", new BigDecimal("90"), day("2026-10-04")));
    }
}
