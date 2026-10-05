package com.fc.v2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.fc.v2.mapper.auto.TVehRunLineVerdictRecordMapper;
import com.fc.v2.model.auto.TVehRunLine;
import com.fc.v2.model.auto.TVehRunLineVerdictRecord;
import com.fc.v2.model.custom.RunLineDefect;
import com.fc.v2.model.custom.RunLineVerdict;
import com.fc.v2.service.impl.TVehRunLineServiceImpl;

/**
 * 界次归层口径测试（不启 Spring，桩掉数据层）。
 * 把需求里点过名的坑一个个钉住：折算方向、同一把舍法、压线一套说法、
 * 历史时刻不许新界顶替、交班次序/界次代序、无可用界两栏留空、停用不参与、
 * 存量空交班、分界数缺格/错序指名、屏挂悬空代号指出。
 */
@ExtendWith(MockitoExtension.class)
public class TVehRunLineServiceImplTest {

    @Mock
    private TVehRunLineMapper vehRunLineMapper;

    @Mock
    private TVehRunLineVerdictRecordMapper verdictRecordMapper;

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
        // 缺参连档都不判，也不许留半份归档
        verify(verdictRecordMapper, never()).insert(any(TVehRunLineVerdictRecord.class));
    }

    // ---- 量程：越界值判出档位，给兜底结论，不许塞进立案层 ----

    @Test
    public void outOfRangeRawValueIsJudgedOutOfLevel() {
        // 量程门在请界之前：越界连界册都不翻，直接判出档位
        RunLineVerdict over = service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("100.001"),
                day("2026-10-04"));
        assertEquals(ITVehRunLineService.TEXT_OUT_OF_RANGE, over.getLevelText());
        assertNull(over.getLevel());
        assertNull(over.getRuleCode());
        // 负值同样判出档位
        RunLineVerdict neg = service.verdict(ITVehRunLineService.MEASURE_DURATION, new BigDecimal("-1"),
                day("2026-10-04"));
        assertEquals(ITVehRunLineService.TEXT_OUT_OF_RANGE, neg.getLevelText());
    }

    // ---- 判完即归档在册：说话界快照落册，日后改界也以这一份为准 ----

    @Test
    public void verdictArchivesSnapshotOfSpeakingRule() {
        stubRegister(rule("S1", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                "2020-01-01", null, 0, 0));
        service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("25"),
                day("2026-10-04 09:00:00"));
        ArgumentCaptor<TVehRunLineVerdictRecord> cap =
                ArgumentCaptor.forClass(TVehRunLineVerdictRecord.class);
        verify(verdictRecordMapper).insert(cap.capture());
        TVehRunLineVerdictRecord rec = cap.getValue();
        assertEquals("S1", rec.getRuleCode());
        assertEquals(1, rec.getLevel().intValue());
        assertEquals("提示", rec.getLevelText());
        // 分界数照裁决那一刻快照，日后界册改了也不许拿新版重算
        assertEquals(0, new BigDecimal("60").compareTo(rec.getTh1Max()));
        assertEquals(0, new BigDecimal("100").compareTo(rec.getTh2Max()));
        assertEquals(0, new BigDecimal("120").compareTo(rec.getTh3Max()));
        assertEquals(0, new BigDecimal("90.000").compareTo(rec.getConvertedValue()));
        assertEquals(0, new BigDecimal("25").compareTo(rec.getRawValue()));
        assertEquals(day("2026-10-04 09:00:00"), rec.getAskAt());
        assertNotNull(rec.getArchivedAt());
    }

    @Test
    public void noRuleVerdictStillArchivesWithBlankRuleColumns() {
        stubRegister();
        RunLineVerdict v = service.verdict(ITVehRunLineService.MEASURE_SPEED, new BigDecimal("25"),
                day("2026-10-04"));
        assertEquals(ITVehRunLineService.TEXT_NO_RULE, v.getLevelText());
        ArgumentCaptor<TVehRunLineVerdictRecord> cap =
                ArgumentCaptor.forClass(TVehRunLineVerdictRecord.class);
        verify(verdictRecordMapper).insert(cap.capture());
        assertNull(cap.getValue().getRuleCode());
        assertNull(cap.getValue().getLevel());
    }

    // ---- 新旧两路对压线与越界只许一个说法 ----

    @Test
    public void oldAndNewPathAgreeOnBoundaryAndRange() {
        stubRegister(rule("D1", ITVehRunLineService.MEASURE_DURATION, "4", "8", "12",
                "2020-01-01", null, 0, 0));
        // 旧路收界册单位（小时）：压够线 8.000 → 严管(2)；旧口径"等于上限取高一档"会错判成提示(1)
        assertEquals(2, service.evaluate("D1", new BigDecimal("8.000"), day("2026-10-04")));
        // 压封顶 → 立案(3)，旧口径会落到 3 以上那档
        assertEquals(3, service.evaluate("D1", new BigDecimal("12.000"), day("2026-10-04")));
        // 差一点点没够着起算 → 0（不给层），与新路"未到起算"是同一处归层
        assertEquals(0, service.evaluate("D1", new BigDecimal("3.999"), day("2026-10-04")));
        // 越界值判出档位：旧路原先不看量程，1441 分钟折 24.017 小时会被塞进立案
        assertEquals(0, service.evaluate("D1", new BigDecimal("24.017"), day("2026-10-04")));
    }

    @Test
    public void oldPathUsesHistoricalWindowNotNewestRule() {
        stubRegister(
                rule("OLD", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                        "2024-01-01", "2025-01-01", 0, 0),
                rule("NEW", ITVehRunLineService.MEASURE_SPEED, "10", "20", "30",
                        "2026-01-01", null, 0, 0));
        // 旧账那一刻旧界在位：90 落提示
        assertEquals(1, service.evaluate("OLD", new BigDecimal("90"), day("2024-09-15")));
        // 今年再问旧代号：旧界已交班，请不出，不许拿最新一条顶替
        assertEquals(0, service.evaluate("OLD", new BigDecimal("90"), day("2026-10-04")));
    }

    @Test
    public void oldAvailableListSharesEffectiveFilterAndSpeakOrder() {
        stubRegister(
                rule("OFF", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                        "2020-01-01", null, 0, 1),
                rule("R10", ITVehRunLineService.MEASURE_SPEED, "60", "100", "120",
                        "2020-01-01", null, 2, 0),
                rule("R2", ITVehRunLineService.MEASURE_SPEED, "10", "20", "30",
                        "2020-01-01", null, 2, 0));
        List<TVehRunLine> avail = service.listAvailable(day("2026-10-04"));
        // 停用界不混进可用名单；说话序与裁决同一处排定
        assertEquals(2, avail.size());
        assertEquals("R2", avail.get(0).getRuleCode());
        assertEquals(2, service.countAvailable(day("2026-10-04")));
        // 90 km/h：头一条 R2 判立案；多规则旧路与裁决听同一条说话
        assertEquals(3, service.evaluateTop(new BigDecimal("90"), day("2026-10-04")));
        // 越界值在旧路同样判出档位
        assertEquals(0, service.evaluateTop(new BigDecimal("360.001"), day("2026-10-04")));
    }
}
