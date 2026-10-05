package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TVehHandleFlowMapper;
import com.fc.v2.model.auto.TVehHandleFlow;
import com.fc.v2.service.ITVehHandleFlowService;

/**
 * 严重报警处置单 Service业务层处理（state-machine 形状：单据流转）
 *
 * 状态与环节同源：状态不许各写各的，只由环节一处推导（statusOfStage），
 * 推进、回退落库时两栏同一次算成，两处记录对得齐。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TVehHandleFlowServiceImpl implements ITVehHandleFlowService {

    private static final int MAX_STAGE = 3;
    /** 处置单走到的那一步：未起 */
    private static final int STATUS_NOT_STARTED = 0;
    /** 处置单走到的那一步：在办 */
    private static final int STATUS_ACTIVE = 1;
    /** 处置单走到的那一步：已封尾 */
    private static final int STATUS_TERMINAL = 2;

    @javax.annotation.Resource
    private TVehHandleFlowMapper vehHandleFlowMapper;

    @Override
    public TVehHandleFlow selectTVehHandleFlowById(Long id) {
        return this.vehHandleFlowMapper.selectById(id);
    }

    @Override
    public List<TVehHandleFlow> selectTVehHandleFlowList(QueryWrapper<TVehHandleFlow> queryWrapper) {
        return this.vehHandleFlowMapper.selectList(queryWrapper);
    }

    /**
     * 状态只由环节一处推导：到顶（归档）即已封尾，还没挪步即未起，中间即在办。
     * 推进、回退都走这一处，不许一处写"在办"一处写"已封尾"。
     */
    private static int statusOfStage(int stage) {
        if (stage >= MAX_STAGE) {
            return STATUS_TERMINAL;
        }
        if (stage <= 0) {
            return STATUS_NOT_STARTED;
        }
        return STATUS_ACTIVE;
    }

    /** 封没封尾看环节也看状态：存量两栏对不齐的单，任一栏到顶都按已封尾待 */
    private static boolean isTerminal(TVehHandleFlow r) {
        int stage = r.getStage() == null ? 0 : r.getStage();
        return stage >= MAX_STAGE
                || (r.getStatus() != null && r.getStatus() == STATUS_TERMINAL);
    }

    @Override
    public TVehHandleFlow advance(Long id, String remark) {
        TVehHandleFlow r = this.vehHandleFlowMapper.selectById(id);
        if (r == null || isTerminal(r)) {
            // 已封尾的是终态，不许再挪
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        // 推进一档就是一档；状态与环节同一次算成
        r.setStage(Math.min(st + 1, MAX_STAGE));
        r.setStatus(statusOfStage(r.getStage()));
        r.setLastAction(remark);
        this.vehHandleFlowMapper.updateById(r);
        return r;
    }

    @Override
    public TVehHandleFlow rollback(Long id, String remark) {
        TVehHandleFlow r = this.vehHandleFlowMapper.selectById(id);
        if (r == null || isTerminal(r)) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        if (st <= 0) {
            // 还没挪步，无可退
            return null;
        }
        // 回退一档就是一档，不一竿子退到底；状态与环节同一次算成
        r.setStage(st - 1);
        r.setStatus(statusOfStage(r.getStage()));
        r.setLastAction(remark);
        this.vehHandleFlowMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TVehHandleFlow r = this.vehHandleFlowMapper.selectById(id);
        if (r == null || isTerminal(r)) {
            // 已办结（封尾）不得再改
            return false;
        }
        r.setContent(remark);
        return this.vehHandleFlowMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TVehHandleFlow r = this.vehHandleFlowMapper.selectById(id);
        if (r == null || isTerminal(r)) {
            // 已办结（封尾）不得删除
            return false;
        }
        return this.vehHandleFlowMapper.deleteById(id) > 0;
    }

}
