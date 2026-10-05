package com.fc.v2.service.impl;

import org.springframework.stereotype.Service;

import com.fc.v2.mapper.auto.TVehMoveBillMapper;
import com.fc.v2.model.auto.TVehMoveBill;
import com.fc.v2.service.ITVehMoveBillService;

/**
 * 移交执法核签单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * 状态与档口同源：核签情形只由所在档口一处推导（statusOfNode），
 * 签批、否决、退回落库时两栏同一次算成；本档已落印数随签批一笔一计，
 * 应落人数与已落印数两头对得齐。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TVehMoveBillServiceImpl implements ITVehMoveBillService {

    private static final int MAX_NODE = 2;
    /** 同档算齐办法：一名即可 */
    private static final int MODE_OR = 0;
    /** 同档算齐办法：两名须同判 */
    private static final int MODE_AND = 1;
    /** 核签情形：在核 */
    private static final int STATUS_RUNNING = 0;
    /** 核签情形：已办结 */
    private static final int STATUS_PASS = 1;
    /** 核签情形：已挪回 */
    private static final int STATUS_VETO = 2;

    @javax.annotation.Resource
    private TVehMoveBillMapper vehMoveBillMapper;

    @Override
    public TVehMoveBill selectTVehMoveBillById(Long id) {
        return this.vehMoveBillMapper.selectById(id);
    }

    /** 核签情形只由所在档口一处推导：到核准档即已办结，其余在核 */
    private static int statusOfNode(int node) {
        return node >= MAX_NODE ? STATUS_PASS : STATUS_RUNNING;
    }

    /** 本档算齐线：一名即可的一票即齐；两名须同判的以本档应落人数为准（空着按两名） */
    private static int quorumOf(TVehMoveBill r) {
        if (r.getSignMode() != null && r.getSignMode() == MODE_AND) {
            return r.getNeedCount() != null && r.getNeedCount() > 0 ? r.getNeedCount() : 2;
        }
        return 1;
    }

    @Override
    public TVehMoveBill approve(Long id, String approver, String comment) {
        TVehMoveBill r = this.vehMoveBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        // 已办结、已挪回的是终态，不许再签
        if (r.getStatus() != null && r.getStatus() != STATUS_RUNNING) {
            return null;
        }
        // 签批一笔，本档已落印数计一笔；凑够算齐线才挪档，挪档即重新计印
        int signed = (r.getSignCount() == null ? 0 : r.getSignCount()) + 1;
        if (signed >= quorumOf(r)) {
            int node = (r.getNodeNo() == null ? 0 : r.getNodeNo()) + 1;
            r.setNodeNo(Integer.valueOf(node));
            r.setSignCount(Integer.valueOf(0));
            r.setStatus(Integer.valueOf(statusOfNode(node)));
        } else {
            r.setSignCount(Integer.valueOf(signed));
            r.setStatus(Integer.valueOf(STATUS_RUNNING));
        }
        this.vehMoveBillMapper.updateById(r);
        return r;
    }

    @Override
    public TVehMoveBill reject(Long id, String approver, String comment) {
        TVehMoveBill r = this.vehMoveBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        // 已办结、已挪回的是终态，不许再否决
        if (r.getStatus() != null && r.getStatus() != STATUS_RUNNING) {
            return null;
        }
        // 否决即挪回：情形落"已挪回"，与档口同一次落库
        r.setStatus(Integer.valueOf(STATUS_VETO));
        this.vehMoveBillMapper.updateById(r);
        return r;
    }

    @Override
    public TVehMoveBill rollback(Long id, String comment) {
        TVehMoveBill r = this.vehMoveBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        // 已挪回的是终态，不许再退
        if (r.getStatus() != null && r.getStatus() == STATUS_VETO) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        // 退回上一档口：已落印数不带回，情形随档口重算，两栏同一次落库
        node = Math.max(0, node - 1);
        r.setNodeNo(Integer.valueOf(node));
        r.setSignCount(Integer.valueOf(0));
        r.setStatus(Integer.valueOf(statusOfNode(node)));
        this.vehMoveBillMapper.updateById(r);
        return r;
    }
}
