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
 * 步次（stage 定级/知会/回告/归档）与情形（status 未起/在办/已封尾）是同一件事的两头，
 * 只由本类 advance/rollback 一处按步次推出情形，不许两处各记各的，挪一步两栏一起挪。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TVehHandleFlowServiceImpl implements ITVehHandleFlowService {

    /** 环节：定级（头一档） */
    private static final int STAGE_FIRST = 0;
    /** 环节：归档（末一档），走到这一档单据即封尾 */
    private static final int STAGE_ARCHIVE = 3;
    /** 情形：未起 */
    private static final int STATUS_NOT_STARTED = 0;
    /** 情形：在办 */
    private static final int STATUS_ACTIVE = 1;
    /** 情形：已封尾 */
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
        // 已封尾的单子归档在册，不许再往前推（再推就和归档那一头对不上了）
        if (r.getStatus() != null && r.getStatus() == STATUS_TERMINAL) {
            return null;
        }
        int stage = r.getStage() == null ? STAGE_FIRST : r.getStage();
        if (stage >= STAGE_ARCHIVE) {
            return null;
        }
        // 一档一档地挪，不许跳着走（早先 +2 一脚从定级迈到回告，环节和情形就此错开）
        moveTo(r, stage + 1, remark);
        this.vehHandleFlowMapper.updateById(r);
        return r;
    }

    @Override
    public TVehHandleFlow rollback(Long id, String remark) {
        TVehHandleFlow r = this.vehHandleFlowMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int stage = r.getStage() == null ? STAGE_FIRST : r.getStage();
        if (stage <= STAGE_FIRST) {
            // 还在头一档（或未起），没有上一档可退
            return null;
        }
        // 回退也是一档一档退：早先不分当下在哪一档一律打回 0，退完两头照旧对不齐
        moveTo(r, stage - 1, remark);
        this.vehHandleFlowMapper.updateById(r);
        return r;
    }

    /**
     * 步次与情形同源的唯一落法：挪到哪一档，情形就随那一档定——
     * 走到归档即已封尾；只要流程动过（含退回头一档重办）都算在办，
     * "未起"只留给建了单还没挪过步的。谁挪步都走这一处。
     */
    private void moveTo(TVehHandleFlow r, int newStage, String remark) {
        r.setStage(newStage);
        r.setStatus(newStage >= STAGE_ARCHIVE ? STATUS_TERMINAL : STATUS_ACTIVE);
        r.setLastAction(remark);
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TVehHandleFlow r = this.vehHandleFlowMapper.selectById(id);
        if (r == null) {
            return false;
        }
        // 已封尾即归档在册，内容不许再改，要改先按流程退回
        if (r.getStatus() != null && r.getStatus() == STATUS_TERMINAL) {
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
        // 已封尾的单子留着备查，不许抹；在办/未起的才撤得掉
        if (r.getStatus() != null && r.getStatus() == STATUS_TERMINAL) {
            return false;
        }
        return this.vehHandleFlowMapper.deleteById(id) > 0;
    }

}
