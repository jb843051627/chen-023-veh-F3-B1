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
 * 处置单状态与环节同源口径测试（不启 Spring，桩掉数据层）。
 * 一档一档挪：stage 0..3，走到归档(3)即封尾(2)；
 * 推进与回退对 stage/status 两栏的改法同出一处，已封尾的不许再推、不许改、不许抹。
 */
@ExtendWith(MockitoExtension.class)
public class TVehHandleFlowServiceImplTest {

    @Mock
    private TVehHandleFlowMapper vehHandleFlowMapper;

    @InjectMocks
    private TVehHandleFlowServiceImpl service;

    private TVehHandleFlow flow(Integer stage, Integer status) {
        TVehHandleFlow r = new TVehHandleFlow();
        r.setId(1L);
        r.setBizNo("BIZ1");
        r.setStage(stage);
        r.setStatus(status);
        r.setDelFlag(0);
        return r;
    }

    @Test
    public void advanceMovesOneStageAtATimeAndKeepsStatusInStep() {
        when(vehHandleFlowMapper.selectById(1L)).thenReturn(flow(0, 0));
        TVehHandleFlow r = service.advance(1L, "定级");
        // 早先 +2 一脚从定级迈到回告；现在一档一档走，且在办与环节同源
        assertEquals(1, r.getStage().intValue());
        assertEquals(1, r.getStatus().intValue());
        assertEquals("定级", r.getLastAction());
    }

    @Test
    public void advanceToArchiveClosesTheFlow() {
        when(vehHandleFlowMapper.selectById(1L)).thenReturn(flow(2, 1));
        TVehHandleFlow r = service.advance(1L, "归档");
        assertEquals(3, r.getStage().intValue());
        assertEquals(2, r.getStatus().intValue());
    }

    @Test
    public void closedFlowCannotAdvanceChangeOrBeRemoved() {
        when(vehHandleFlowMapper.selectById(1L)).thenReturn(flow(3, 2));
        assertNull(service.advance(1L, "再推"));
        assertFalse(service.updateContent(1L, "改内容"));
        assertFalse(service.remove(1L));
        verify(vehHandleFlowMapper, never()).updateById(any());
        verify(vehHandleFlowMapper, never()).deleteById(any());
    }

    @Test
    public void rollbackMovesOneStageBackAndStaysActive() {
        when(vehHandleFlowMapper.selectById(1L)).thenReturn(flow(2, 1));
        TVehHandleFlow r = service.rollback(1L, "退一档");
        assertEquals(1, r.getStage().intValue());
        assertEquals(1, r.getStatus().intValue());
    }

    @Test
    public void rollbackAtFirstStageIsRejected() {
        when(vehHandleFlowMapper.selectById(1L)).thenReturn(flow(0, 0));
        assertNull(service.rollback(1L, "退无可退"));
    }

    @Test
    public void missingFlowReturnsNull() {
        when(vehHandleFlowMapper.selectById(9L)).thenReturn(null);
        assertNull(service.advance(9L, "x"));
        assertNull(service.rollback(9L, "x"));
        assertFalse(service.remove(9L));
        assertTrue(true);
    }
}
