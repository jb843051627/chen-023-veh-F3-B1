package com.fc.v2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * 移交核签单测试（不启 Spring，桩掉数据层）。
 * 先在本档落印，落齐应到人数才过档，过了核准才办结；
 * 否决即终态；退回上一档本档落印作废。签批、否决、退回三处口径同源。
 */
@ExtendWith(MockitoExtension.class)
public class TVehMoveBillServiceImplTest {

    @Mock
    private TVehMoveBillMapper vehMoveBillMapper;

    @InjectMocks
    private TVehMoveBillServiceImpl service;

    private TVehMoveBill bill(Integer node, Integer signMode, Integer need, Integer signed, Integer status) {
        TVehMoveBill b = new TVehMoveBill();
        b.setId(1L);
        b.setBillNo("QY1");
        b.setNodeNo(node);
        b.setSignMode(signMode);
        b.setNeedCount(need);
        b.setSignCount(signed);
        b.setStatus(status);
        b.setDelFlag(0);
        return b;
    }

    @Test
    public void firstSignDoesNotPassNodeYetUnderTwoPersonMode() {
        // 两名须同判、本档应到 2 人：头一票落印但档口不动，仍在核
        when(vehMoveBillMapper.selectById(1L)).thenReturn(bill(0, 1, 2, 0, 0));
        TVehMoveBill b = service.approve(1L, "甲", null);
        assertEquals(0, b.getNodeNo().intValue());
        assertEquals(1, b.getSignCount().intValue());
        assertEquals(0, b.getStatus().intValue());
    }

    @Test
    public void quotaFilledAdvancesNodeAndResetsSignCount() {
        when(vehMoveBillMapper.selectById(1L)).thenReturn(bill(0, 1, 2, 1, 0));
        TVehMoveBill b = service.approve(1L, "乙", null);
        // 早先先把 nodeNo +1 再拿旧值判过档，永远差一档卡在在核；现在齐了才过档
        assertEquals(1, b.getNodeNo().intValue());
        assertEquals(0, b.getSignCount().intValue());
        assertEquals(0, b.getStatus().intValue());
    }

    @Test
    public void passingLastNodeClosesTheBill() {
        // 初核/复核/核准三档，一名即可：核准档一票即办结
        when(vehMoveBillMapper.selectById(1L)).thenReturn(bill(2, 0, 1, 0, 0));
        TVehMoveBill b = service.approve(1L, "核准人", null);
        assertEquals(2, b.getNodeNo().intValue());
        assertEquals(1, b.getStatus().intValue());
    }

    @Test
    public void missingNeedCountDefaultsBySignMode() {
        // 册上应到人数空着：两名同判按 2 算，第一票不过档
        when(vehMoveBillMapper.selectById(1L)).thenReturn(bill(0, 1, null, 0, 0));
        TVehMoveBill b = service.approve(1L, "甲", null);
        assertEquals(0, b.getNodeNo().intValue());
        assertEquals(1, b.getSignCount().intValue());
    }

    @Test
    public void rejectClosesTheBillAsVeto() {
        when(vehMoveBillMapper.selectById(1L)).thenReturn(bill(0, 0, 1, 0, 0));
        TVehMoveBill b = service.reject(1L, "甲", "材料不全");
        // 早先只空落一次 update，情形仍挂"在核"
        assertEquals(2, b.getStatus().intValue());
    }

    @Test
    public void closedBillAcceptsNoMoreSigns() {
        when(vehMoveBillMapper.selectById(1L)).thenReturn(bill(2, 0, 1, 0, 1));
        assertNull(service.approve(1L, "再签", null));
        when(vehMoveBillMapper.selectById(1L)).thenReturn(bill(0, 0, 1, 0, 2));
        assertNull(service.approve(1L, "否决后再签", null));
    }

    @Test
    public void rollbackReturnsToPreviousNodeAndClearsSigns() {
        when(vehMoveBillMapper.selectById(1L)).thenReturn(bill(1, 1, 2, 2, 0));
        TVehMoveBill b = service.rollback(1L, "退回复核");
        assertEquals(0, b.getNodeNo().intValue());
        assertEquals(0, b.getSignCount().intValue());
        assertEquals(0, b.getStatus().intValue());
    }

    @Test
    public void rollbackAtFirstNodeRejected() {
        when(vehMoveBillMapper.selectById(1L)).thenReturn(bill(0, 0, 1, 1, 0));
        assertNull(service.rollback(1L, "退无可退"));
    }
}
