package com.fc.v2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

import com.fc.v2.mapper.auto.TVehDueTaskMapper;
import com.fc.v2.model.auto.TVehDueTask;
import com.fc.v2.service.impl.TVehDueTaskServiceImpl;

/**
 * 证照到期催办条目口径测试（不启 Spring，桩掉数据层）。
 * 钉住：只派待派、恰好到期算到期、量程窗口 2~5 日、判据缺失给兜底（分不出）不抛异常、
 * 空册返回 0 不甩异常、每条结果落库。
 */
@ExtendWith(MockitoExtension.class)
public class TVehDueTaskServiceImplTest {

    @Mock
    private TVehDueTaskMapper vehDueTaskMapper;

    @InjectMocks
    private TVehDueTaskServiceImpl service;

    private static Date day(String s) {
        try {
            return new SimpleDateFormat(s.length() <= 10 ? "yyyy-MM-dd" : "yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static TVehDueTask task(long id, String dueAt, String amount, Integer status) {
        TVehDueTask r = new TVehDueTask();
        r.setId(id);
        r.setItemNo("ITEM" + id);
        r.setDueAt(dueAt == null ? null : day(dueAt));
        r.setAmount(amount == null ? null : new BigDecimal(amount));
        r.setStatus(status);
        r.setDelFlag(0);
        return r;
    }

    private void stubTasks(TVehDueTask... tasks) {
        when(vehDueTaskMapper.selectList(any())).thenReturn(new ArrayList<TVehDueTask>(Arrays.asList(tasks)));
    }

    @Test
    public void listDuePicksOnlyWaitingAndDue() {
        stubTasks(
                task(1L, "2026-10-04 08:00:00", "3", 0),   // 待派＋已到期 → 该开口
                task(2L, "2026-10-04 12:00:00", "3", 0),  // 待派＋恰好到期 → 也算到期
                task(3L, "2026-10-05 00:00:00", "3", 0),  // 还没到止点 → 不派
                task(4L, "2026-10-04 08:00:00", "3", 1),  // 已派出 → 不回锅
                task(5L, "2026-10-04 08:00:00", "3", 2)); // 分不出 → 不回锅
        List<TVehDueTask> due = service.listDue(day("2026-10-04 12:00:00"));
        assertEquals(2, due.size());
        assertEquals(1L, due.get(0).getId().longValue());
        assertEquals(2L, due.get(1).getId().longValue());
    }

    @Test
    public void listDueNullAtGivesEmptyNotException() {
        // 判据（问话时刻）缺失：给兜底空单，不甩异常
        assertTrue(service.listDue(null).isEmpty());
    }

    @Test
    public void runOnceEmptyGivesZeroNotException() {
        stubTasks();
        // 一条都够不上：返回 0，不许 get(0) 甩异常
        assertEquals(0, service.runOnce(day("2026-10-04 12:00:00")));
        verify(vehDueTaskMapper, never()).updateById(any());
    }

    @Test
    public void runOnceNullAtGivesZero() {
        assertEquals(0, service.runOnce(null));
    }

    @Test
    public void runOnceDispatchesInWindowAndFailsOutOfWindow() {
        stubTasks(
                task(1L, "2026-10-04 08:00:00", "3", 0),    // 窗口内 → 已派出
                task(2L, "2026-10-04 08:00:00", "2", 0),    // 贴窗口下沿 → 已派出
                task(3L, "2026-10-04 08:00:00", "5", 0),    // 贴窗口上沿 → 已派出
                task(4L, "2026-10-04 08:00:00", "1.9", 0),  // 越下沿 → 分不出
                task(5L, "2026-10-04 08:00:00", "5.1", 0),  // 越上沿 → 分不出
                task(6L, "2026-10-04 08:00:00", null, 0));  // 判据缺失 → 兜底分不出，不抛异常
        int ok = service.runOnce(day("2026-10-04 12:00:00"));
        assertEquals(3, ok);
        ArgumentCaptor<TVehDueTask> cap = ArgumentCaptor.forClass(TVehDueTask.class);
        verify(vehDueTaskMapper, times(6)).updateById(cap.capture());
        List<TVehDueTask> written = cap.getAllValues();
        // 每条都落库：窗口内记已派出(1)，窗口外或缺据记分不出(2)
        assertEquals(1, written.get(0).getStatus().intValue());
        assertEquals(1, written.get(1).getStatus().intValue());
        assertEquals(1, written.get(2).getStatus().intValue());
        assertEquals(2, written.get(3).getStatus().intValue());
        assertEquals(2, written.get(4).getStatus().intValue());
        assertEquals(2, written.get(5).getStatus().intValue());
        assertEquals(6L, written.get(5).getId().longValue());
    }
}
