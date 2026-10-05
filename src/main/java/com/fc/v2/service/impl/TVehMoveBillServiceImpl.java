package com.fc.v2.service.impl;

import org.springframework.stereotype.Service;

import com.fc.v2.mapper.auto.TVehMoveBillMapper;
import com.fc.v2.model.auto.TVehMoveBill;
import com.fc.v2.service.ITVehMoveBillService;

/**
 * 移交执法核签单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * 档口（node_no 初核/复核/核准）、本档落印数（sign_count）与核签情形（status）
 * 只由本类一处同落：先在本档落印，落齐本档应到人数才过档，过了末档才办结；
 * 签批、否决、退回三个动作对这三栏的改法不许各走各的。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TVehMoveBillServiceImpl implements ITVehMoveBillService {

    /** 末档：核准（0初核 1复核 2核准） */
    private static final int MAX_NODE = 2;
    /** 同档算齐办法：一名即可 */
    private static final int MODE_OR = 0;
    /** 同档算齐办法：两名须同判 */
    private static final int MODE_AND = 1;
    /** 情形：在核 */
    private static final int STATUS_RUNNING = 0;
    /** 情形：已办结 */
    private static final int STATUS_PASS = 1;
    /** 情形：已否决（终态，与办结一样封档） */
    private static final int STATUS_VETO = 2;

    @javax.annotation.Resource
    private TVehMoveBillMapper vehMoveBillMapper;

    @Override
    public TVehMoveBill selectTVehMoveBillById(Long id) {
        return this.vehMoveBillMapper.selectById(id);
    }

    @Override
    public TVehMoveBill approve(Long id, String approver, String comment) {
        TVehMoveBill r = this.vehMoveBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        // 已办结、已否决的单子封了档，不再落印
        if (r.getStatus() != null && r.getStatus() != STATUS_RUNNING) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        if (node > MAX_NODE) {
            return null;
        }
        // 先在本档落印，再数本档落齐没有：早先先把档口 +1、再拿旧档口判过档，
        // 结果永远差一档，单子卡在"在核"过不去
        int signed = (r.getSignCount() == null ? 0 : r.getSignCount()) + 1;
        r.setSignCount(signed);
        if (signed >= needCount(r)) {
            // 本档算齐：落印数清零，进到下一档（新档从头签起）；过了核准即办结
            r.setSignCount(0);
            if (node >= MAX_NODE) {
                r.setNodeNo(MAX_NODE);
                r.setStatus(STATUS_PASS);
            } else {
                r.setNodeNo(node + 1);
                r.setStatus(STATUS_RUNNING);
            }
        } else {
            // 本档还没签齐：档口不动，继续在核
            r.setNodeNo(node);
            r.setStatus(STATUS_RUNNING);
        }
        if (comment != null) {
            r.setRemark(comment);
        }
        this.vehMoveBillMapper.updateById(r);
        return r;
    }

    @Override
    public TVehMoveBill reject(Long id, String approver, String comment) {
        TVehMoveBill r = this.vehMoveBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        if (r.getStatus() != null && r.getStatus() != STATUS_RUNNING) {
            return null;
        }
        // 否决即终态：早先只空落了一次 update，情形仍摆"在核"，两头对同一动作说法不一
        r.setStatus(STATUS_VETO);
        if (comment != null) {
            r.setRemark(comment);
        }
        this.vehMoveBillMapper.updateById(r);
        return r;
    }

    @Override
    public TVehMoveBill rollback(Long id, String comment) {
        TVehMoveBill r = this.vehMoveBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        if (node <= 0) {
            // 还在初核，没有上一档可退
            return null;
        }
        // 退回上一档口继续在核；本档已落的印随退回作废，新档从头签起，
        // 情形与签批走同一处改法，不许退回后仍挂着旧档口的印数
        r.setNodeNo(node - 1);
        r.setSignCount(0);
        r.setStatus(STATUS_RUNNING);
        if (comment != null) {
            r.setRemark(comment);
        }
        this.vehMoveBillMapper.updateById(r);
        return r;
    }

    /** 本档应落人数：册上写了照册上的；空着按算齐办法补（一名即可 1、两名同判 2） */
    private int needCount(TVehMoveBill r) {
        if (r.getNeedCount() != null && r.getNeedCount() > 0) {
            return r.getNeedCount().intValue();
        }
        return r.getSignMode() != null && r.getSignMode() == MODE_AND ? 2 : 1;
    }
}
