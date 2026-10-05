package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TVehDueTask;

/**
 * 证照到期催办条目 Service接口（scheduling-job 形状：周期执行，无增删改查入口）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITVehDueTaskService {

    /** 按主键回查条目 */
    TVehDueTask selectTVehDueTaskById(Long id);

    /** 该时刻该开口的条目（到了止点时刻含恰好到期 + 尚未处理 + 未留痕删除）；at 空着回空单 */
    List<TVehDueTask> listDue(Date at);

    /**
     * 执行一次：提前量落在 2~5 日窗口内的派出（记"已派出"），
     * 窗口外或判据缺失的记"分不出"（兜底结论，不抛异常），每条都落库。
     * 返回**派出条数**；一条都够不上返回 0。
     */
    int runOnce(Date at);
}
