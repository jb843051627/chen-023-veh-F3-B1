package com.fc.v2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TVehMoveBillMapper;
import com.fc.v2.model.auto.TVehMoveBill;
import com.fc.v2.service.impl.TVehMoveBillServiceImpl;

/**
 * 移交执法核签单口径测试（不启 Spring，桩掉数据层）。
 * 钉住：签批一笔计一印、凑够算齐线才挪档（一名即可/两名须同判）、
 * 挪档重新计印、状态只由档口一处推导、否决落"已挪回"、终态不许再动。
 */
@ExtendWith(MockitoExtension.class)
public class TVehMoveBillServiceImplTest {

    @Mock
    private TVehMoveBillMapper vehMoveBillMapper;

    @InjectMocks
    private TVehMoveBillServiceImpl service;

    private static TVehMoveBill bill(long id, Integer node, Integer mode, Integer need,
                                     Integer signed, Integer status) {
        TVehMoveBill r = new TVehMoveBill();
        r.setId(id);
        r.setBillNo("BILL" + id);
        r.setNodeNo(node);
        r.setSignMode(mode);
        r.setNeedCount(need);
        r.setSignCount(signed);
        r.setStatus(status);
        r.setDelFlag(0);
        return r;
    }

    @Test
    public void orModeSingleSignatureAdvancesNode() {
        when(vehMoveBillMapper.selectById(1L)).thenReturn(bill(1L, 0, 0, null, 0, 0));
        TVehMoveBill r = service.approve(1L, "张三", "可");
        // 一名即可：一票即齐，挪一档并重新计印，情形随档口推导（在核）
        assertEquals(1, r.getNodeNo().intValue());
        assertEquals(0, r.getSignCount().intValue());
        assertEquals(0, r.getStatus().intValue());
        verify(vehMoveBillMapper).updateById(any());
    }

    @Test
    public void andModeWaitsForQuorum() {
        // 两名须同判：第一票只计印不挪档
        when(vehMoveBillMapper.selectById(1L)).thenReturn(bill(1L, 0, 1, 2, 0, 0));
        TVehMoveBill r = service.approve(1L, "张三", "可");
        assertEquals(0, r.getNodeNo().intValue());
        assertEquals(1, r.getSignCount().intValue());
        assertEquals(0, r.getStatus().intValue());
    }

    @Test
    public void andModeSecondSignatureAdvances() {
        when(vehMoveBillMapper.selectById(1L)).thenReturn(bill(1L, 0, 1, 2, 1, 0));
        TVehMoveBill r = service.approve(1L, "李四", "可");
        // 第二票凑齐：挪档、重新计印，应落人数与已落印数两头对得齐
        assertEquals(1, r.getNodeNo().intValue());
        assertEquals(0, r.getSignCount().intValue());
    }

    @Test
    public void andModeDefaultsToTwoWhenNeedCountBlank() {
        when(vehMoveBillMapper.selectById(1L)).thenReturn(bill(1L, 0, 1, null, 0, 0));
        TVehMoveBill r = service.approve(1L, "张三", "可");
        // 应落人数空着按两名：第一票不挪档
        assertEquals(0, r.getNodeNo().intValue());
        assertEquals(1, r.getSignCount().intValue());
    }

    @Test
    public void reachingFinalNodePasses() {
        when(vehMoveBillMapper.selectById(1L)).thenReturn(bill(1L, 1, 0, null, 0, 0));
        TVehMoveBill r = service.approve(1L, "张三", "可");
        // 到核准档：情形随档口落"已办结"，两栏同一次算成
        assertEquals(2, r.getNodeNo().intValue());
        assertEquals(1, r.getStatus().intValue());
    }

    @Test
    public void terminalBillRejectsFurtherActions() {
        when(vehMoveBillMapper.selectById(1L)).thenReturn(bill(1L, 2, 0, null, 0, 1));
        assertNull(service.approve(1L, "张三", "可"));
        assertNull(service.reject(1L, "张三", "否"));
        verify(vehMoveBillMapper, never()).updateById(any());
    }

    @Test
    public void rejectMarksVeto() {
        when(vehMoveBillMapper.selectById(1L)).thenReturn(bill(1L, 0, 0, null, 0, 0));
        TVehMoveBill r = service.reject(1L, "张三", "否");
        // 否决即挪回：情形落"已挪回"
        assertEquals(2, r.getStatus().intValue());
        verify(vehMoveBillMapper).updateById(any());
    }

    @Test
    public void vetoedBillIsTerminal() {
        when(vehMoveBillMapper.selectById(1L)).thenReturn(bill(1L, 0, 0, null, 0, 2));
        assertNull(service.approve(1L, "张三", "可"));
        assertNull(service.rollback(1L, "退"));
        verify(vehMoveBillMapper, never()).updateById(any());
    }

    @Test
    public void rollbackRecomputesStatusFromNode() {
        when(vehMoveBillMapper.selectById(1L)).thenReturn(bill(1L, 2, 0, null, 0, 1));
        TVehMoveBill r = service.rollback(1L, "退回复核");
        // 退回上一档口：情形随档口重算回"在核"，已落印数不带回
        assertEquals(1, r.getNodeNo().intValue());
        assertEquals(0, r.getStatus().intValue());
        assertEquals(0, r.getSignCount().intValue());
        verify(vehMoveBillMapper).updateById(any());
    }
}
