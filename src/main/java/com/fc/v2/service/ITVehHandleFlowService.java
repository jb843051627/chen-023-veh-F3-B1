package com.fc.v2.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TVehHandleFlow;

/**
 * 严重报警处置单 Service接口（state-machine 形状：单据流转，不提供增删改查全套）
 *
 * 状态与环节同源：状态只由环节一处推导（0未起/1在办/2已封尾），
 * 推进、回退落库时两栏同一次算成，两处记录对得齐。已封尾为终态，不许再挪、再改、再删。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITVehHandleFlowService {

    /** 按主键查询单据 */
    TVehHandleFlow selectTVehHandleFlowById(Long id);

    /** 列表查询（流转台账） */
    List<TVehHandleFlow> selectTVehHandleFlowList(QueryWrapper<TVehHandleFlow> queryWrapper);

    /** 推进一档（就是一档）：状态随环节同一次算成；已封尾被拒返回 null */
    TVehHandleFlow advance(Long id, String remark);

    /** 回退一档（不一竿子退到底）：状态随环节同一次算成；未起或已封尾被拒返回 null */
    TVehHandleFlow rollback(Long id, String remark);

    /** 修改内容：已办结（封尾）不得再改，被拒返回 false */
    boolean updateContent(Long id, String remark);

    /** 删除单据：已办结（封尾）不得删除，被拒返回 false */
    boolean remove(Long id);
}
