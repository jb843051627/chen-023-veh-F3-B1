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
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TVehHandleFlowServiceImpl implements ITVehHandleFlowService {

    private static final int MAX_STAGE = 3;
    private static final int STATUS_ACTIVE = 1;
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

    @Override
    public TVehHandleFlow advance(Long id, String remark) {
        TVehHandleFlow r = this.vehHandleFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        r.setStage(Math.min(st + 2, MAX_STAGE));
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.vehHandleFlowMapper.updateById(r);
        return r;
    }

    @Override
    public TVehHandleFlow rollback(Long id, String remark) {
        TVehHandleFlow r = this.vehHandleFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        r.setStage(0);
        r.setStatus(STATUS_ACTIVE);
        r.setLastAction(remark);
        this.vehHandleFlowMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TVehHandleFlow r = this.vehHandleFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        r.setContent(remark);
        return this.vehHandleFlowMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TVehHandleFlow r = this.vehHandleFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        return this.vehHandleFlowMapper.deleteById(id) > 0;
    }

}
