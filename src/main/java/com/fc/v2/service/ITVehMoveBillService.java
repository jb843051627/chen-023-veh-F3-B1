package com.fc.v2.service;

import com.fc.v2.model.auto.TVehMoveBill;

/**
 * 移交执法核签单 Service接口（approval-chain 形状：多阶段签批，无增删改查入口）
 *
 * 状态与档口同源：核签情形（0在核/1已办结/2已挪回）只由所在档口一处推导。
 * 本档已落印数随签批一笔一计，凑够算齐线（一名即可一票即齐；两名须同判以应落人数为准）
 * 才挪档，挪档即重新计印；应落人数与已落印数两头对得齐。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITVehMoveBillService {

    /** 按主键回查单据 */
    TVehMoveBill selectTVehMoveBillById(Long id);

    /** 签批一票：计印、凑够算齐线挪档；已办结/已挪回被拒返回 null */
    TVehMoveBill approve(Long id, String approver, String comment);

    /** 否决：情形落"已挪回"；已办结/已挪回被拒返回 null */
    TVehMoveBill reject(Long id, String approver, String comment);

    /** 退回上一环节：已落印数不带回、情形随档口重算；已挪回被拒返回 null */
    TVehMoveBill rollback(Long id, String comment);
}
