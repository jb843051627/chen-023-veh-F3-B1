package com.fc.v2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TVehHandleFlowMapper;
import com.fc.v2.model.auto.TVehHandleFlow;
import com.fc.v2.service.impl.TVehHandleFlowServiceImpl;

/**
 * 严重报警处置单口径测试（不启 Spring，桩掉数据层）。
 * 钉住：推进/回退一次就是一档、状态只由环节一处推导（同源）、
 * 已封尾为终态不许再挪再改再删、存量两栏对不齐的单按封尾待。
 */
@ExtendWith(MockitoExtension.class)
public class TVehHandleFlowServiceImplTest {

    @Mock
    private TVehHandleFlowMapper vehHandleFlowMapper;

    @InjectMocks
    private TVehHandleFlowServiceImpl service;

    private static TVehHandleFlow flow(long id, Integer stage, Integer status) {
        TVehHandleFlow r = new TVehHandleFlow();
        r.setId(id);
        r.setBizNo("BIZ" + id);
        r.setStage(stage);
        r.setStatus(status);
        r.setDelFlag(0);
        return r;
    }

    @Test
    public void advanceMovesExactlyOneStageAndDerivesStatus() {
        when(vehHandleFlowMapper.selectById(1L)).thenReturn(flow(1L, 0, 0));
        TVehHandleFlow r = service.advance(1L, "定级毕");
        // 推进一档就是一档（不许一跳两档）；状态随环节同一次算成：0→1 在办
        assertEquals(1, r.getStage().intValue());
        assertEquals(1, r.getStatus().intValue());
        verify(vehHandleFlowMapper).updateById(any());
    }

    @Test
    public void advanceToTopSealsTerminal() {
        when(vehHandleFlowMapper.selectById(1L)).thenReturn(flow(1L, 2, 1));
        TVehHandleFlow r = service.advance(1L, "回告毕");
        // 到顶（归档）：状态与环节同一次落"已封尾"，两处对得齐
        assertEquals(3, r.getStage().intValue());
        assertEquals(2, r.getStatus().intValue());
    }

    @Test
    public void advanceOnTerminalRejected() {
        when(vehHandleFlowMapper.selectById(1L)).thenReturn(flow(1L, 3, 2));
        assertNull(service.advance(1L, "再挪"));
        verify(vehHandleFlowMapper, never()).updateById(any());
    }

    @Test
    public void legacyRowWithTerminalStatusIsAlsoTerminal() {
        // 存量两栏对不齐的单：环节没到顶但状态已封尾，照样按终态待
        when(vehHandleFlowMapper.selectById(1L)).thenReturn(flow(1L, 2, 2));
        assertNull(service.advance(1L, "再挪"));
        assertFalse(service.updateContent(1L, "改"));
        assertFalse(service.remove(1L));
    }

    @Test
    public void rollbackMovesExactlyOneStageBack() {
        when(vehHandleFlowMapper.selectById(1L)).thenReturn(flow(1L, 2, 1));
        TVehHandleFlow r = service.rollback(1L, "退回");
        // 回退一档就是一档，不一竿子退到底；状态随环节同一次算成
        assertEquals(1, r.getStage().intValue());
        assertEquals(1, r.getStatus().intValue());
    }

    @Test
    public void rollbackToStartMarksNotStarted() {
        when(vehHandleFlowMapper.selectById(1L)).thenReturn(flow(1L, 1, 1));
        TVehHandleFlow r = service.rollback(1L, "退回");
        assertEquals(0, r.getStage().intValue());
        assertEquals(0, r.getStatus().intValue());
    }

    @Test
    public void rollbackAtStartRejected() {
        when(vehHandleFlowMapper.selectById(1L)).thenReturn(flow(1L, 0, 0));
        assertNull(service.rollback(1L, "无可退"));
        verify(vehHandleFlowMapper, never()).updateById(any());
    }

    @Test
    public void terminalCannotEditOrRemove() {
        when(vehHandleFlowMapper.selectById(1L)).thenReturn(flow(1L, 3, 2));
        assertFalse(service.updateContent(1L, "改"));
        assertFalse(service.remove(1L));
        verify(vehHandleFlowMapper, never()).updateById(any());
        verify(vehHandleFlowMapper, never()).deleteById(any(Long.class));
    }

    @Test
    public void activeCanEditAndRemove() {
        when(vehHandleFlowMapper.selectById(1L)).thenReturn(flow(1L, 1, 1));
        when(vehHandleFlowMapper.updateById(any())).thenReturn(1);
        when(vehHandleFlowMapper.deleteById(1L)).thenReturn(1);
        assertTrue(service.updateContent(1L, "改"));
        assertTrue(service.remove(1L));
    }
}
