package com.fc.v2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TVehDueTaskMapper;
import com.fc.v2.model.auto.TVehDueTask;
import com.fc.v2.service.impl.TVehDueTaskServiceImpl;

/**
 * 到期催办测试（不启 Spring，桩掉数据层）。
 * 开口窗口 [止点-提前日, …] 含开口那一刻、逾期照催；只派待派（存量空状态照待派认）；
 * runOnce 空名单给 0 兜底（早先取 get(0) 直接崩），单条派砸不牵动别条。
 */
@ExtendWith(MockitoExtension.class)
public class TVehDueTaskServiceImplTest {

    @Mock
    private TVehDueTaskMapper vehDueTaskMapper;

    @InjectMocks
    private TVehDueTaskServiceImpl service;

    private static Date ts(String s) {
        try {
            return new SimpleDateFormat(s.length() <= 10 ? "yyyy-MM-dd" : "yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TVehDueTask task(String itemNo, String dueAt, String aheadDays, Integer status) {
        TVehDueTask t = new TVehDueTask();
        t.setId((long) (itemNo.hashCode() & 0x7fffffff));
        t.setItemNo(itemNo);
        t.setDueAt(ts(dueAt));
        t.setAmount(aheadDays == null ? null : new BigDecimal(aheadDays));
        t.setStatus(status);
        t.setDelFlag(0);
        return t;
    }

    private void stubAll(TVehDueTask... tasks) {
        when(vehDueTaskMapper.selectList(any()))
                .thenReturn(new ArrayList<TVehDueTask>(Arrays.asList(tasks)));
    }

    @Test
    public void windowOpensAheadDaysAndIsInclusive() {
        // 止点 10-10，最可提前 2 日：10-08 零点开口
        stubAll(task("T1", "2026-10-10 00:00:00", "2", 0));
        assertEquals(0, service.listDue(ts("2026-10-07 23:59:59")).size());
        assertEquals(1, service.listDue(ts("2026-10-08 00:00:00")).size());
        // 过了止点逾期照催
        assertEquals(1, service.listDue(ts("2026-10-20 00:00:00")).size());
    }

    @Test
    public void nullAheadDaysOpensOnDuePointInclusive() {
        // 存量数据提前日空着：到止点那一刻（含）才开口
        stubAll(task("LEG", "2026-10-10 00:00:00", null, null));
        assertEquals(0, service.listDue(ts("2026-10-09 23:59:59")).size());
        List<TVehDueTask> due = service.listDue(ts("2026-10-10 00:00:00"));
        assertEquals(1, due.size());
        assertEquals("LEG", due.get(0).getItemNo());
    }

    @Test
    public void dispatchedOrFailedTasksAreNotDueAgain() {
        stubAll(
                task("DONE", "2026-10-01 00:00:00", "0", 1),
                task("FAIL", "2026-10-01 00:00:00", "0", 2),
                task("WAIT", "2026-10-01 00:00:00", "0", 0));
        List<TVehDueTask> due = service.listDue(ts("2026-10-05 00:00:00"));
        assertEquals(1, due.size());
        assertEquals("WAIT", due.get(0).getItemNo());
    }

    @Test
    public void runOnceOnEmptyListReturnsZeroWithoutBlowingUp() {
        // 早先 due.get(0) 在空名单上直接抛 IndexOutOfBounds，调用方收异常而非兜底值
        stubAll();
        assertEquals(0, service.runOnce(ts("2026-10-05 00:00:00")));
    }

    @Test
    public void runOnceNullMomentReturnsZero() {
        assertEquals(0, service.runOnce(null));
    }

    @Test
    public void runOnceDispatchesOnlyDueWaitingTasks() {
        stubAll(
                task("DUE1", "2026-10-01 00:00:00", "0", 0),
                task("DUE2", "2026-10-02 00:00:00", "0", 0),
                task("FUTURE", "2026-12-01 00:00:00", "0", 0));
        when(vehDueTaskMapper.updateById(any(TVehDueTask.class))).thenReturn(1);
        int ok = service.runOnce(ts("2026-10-05 00:00:00"));
        // 名单与派出口径同源：只派到期待派两条，未到期的不碰
        assertEquals(2, ok);
        verify(vehDueTaskMapper, never()).updateById(org.mockito.ArgumentMatchers.argThat(
                t -> t != null && "FUTURE".equals(t.getItemNo())));
    }

    @Test
    public void oneRowFailingDoesNotStopTheRest() {
        TVehDueTask a = task("A", "2026-10-01 00:00:00", "0", 0);
        TVehDueTask b = task("B", "2026-10-01 00:00:00", "0", 0);
        stubAll(a, b);
        when(vehDueTaskMapper.updateById(any(TVehDueTask.class)))
                .thenThrow(new RuntimeException("库抖了一下"))
                .thenReturn(1);
        assertEquals(1, service.runOnce(ts("2026-10-05 00:00:00")));
    }
}
