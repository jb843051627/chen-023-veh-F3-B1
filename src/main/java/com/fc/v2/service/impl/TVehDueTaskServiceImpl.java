package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TVehDueTaskMapper;
import com.fc.v2.model.auto.TVehDueTask;
import com.fc.v2.service.ITVehDueTaskService;

/**
 * 证照到期催办条目 Service业务层处理（scheduling-job 形状：周期执行）
 *
 * 窗口判定只在 runOnce 一处：提前开口量程 2~5 日，窗口内的派出、窗口外或判据缺失的
 * 记"分不出"——判据缺失给兜底值，不许把异常甩给调用方。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TVehDueTaskServiceImpl implements ITVehDueTaskService {

    /** 提前开口量程：最可提前 2 日 */
    private static final BigDecimal WIN_FROM = new BigDecimal("2");
    /** 提前开口量程：最可提前 5 日 */
    private static final BigDecimal WIN_TO = new BigDecimal("5");
    /** 条目情形：待派 */
    private static final int STATUS_WAIT = 0;
    /** 条目情形：已派出 */
    private static final int STATUS_DONE = 1;
    /** 条目情形：分不出 */
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TVehDueTaskMapper vehDueTaskMapper;

    @Override
    public TVehDueTask selectTVehDueTaskById(Long id) {
        return this.vehDueTaskMapper.selectById(id);
    }

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）：JDBC 的 serverTimezone 与本机
     * 时区不对称，直接把 java.util.Date 作参数会整体偏移，使"恰好到期"这类边界用例错判。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    /**
     * 该时刻该开口的条目：到了止点时刻（恰好到期也算）＋尚未处理（待派）＋未留痕删除。
     * 量程窗口不在这里卡——窗口判定只在 runOnce 一处，两处不各卡各的。
     */
    @Override
    public List<TVehDueTask> listDue(Date at) {
        List<TVehDueTask> due = new java.util.ArrayList<TVehDueTask>();
        if (at == null) {
            return due;
        }
        List<TVehDueTask> all = this.vehDueTaskMapper.selectList(
                new QueryWrapper<TVehDueTask>().eq("del_flag", 0));
        String now = ts(at);
        for (TVehDueTask r : all) {
            // 已派出、分不出的不再回锅；只认还在待派的
            if (r.getStatus() == null || r.getStatus() != STATUS_WAIT) {
                continue;
            }
            // 到了止点时刻即该开口，恰好到期也算在内
            if (r.getDueAt() != null && ts(r.getDueAt()).compareTo(now) <= 0) {
                due.add(r);
            }
        }
        return due;
    }

    /**
     * 执行一次：窗口（提前 2~5 日）内的派出，窗口外或判据（提前量）缺失的记"分不出"，
     * 每条都落库；返回派出条数。一条都够不上时返回 0，不许把异常甩给调用方。
     */
    @Override
    public int runOnce(Date at) {
        if (at == null) {
            return 0;
        }
        List<TVehDueTask> due = listDue(at);
        if (due.isEmpty()) {
            return 0;
        }
        int ok = 0;
        for (TVehDueTask r : due) {
            TVehDueTask upd = new TVehDueTask();
            upd.setId(r.getId());
            if (dispatchable(r.getAmount())) {
                upd.setStatus(STATUS_DONE);
                ok++;
            } else {
                // 判据缺失或越出量程：给兜底结论"分不出"，不许抛异常、不许留在待派里反复回锅
                upd.setStatus(STATUS_FAIL);
            }
            this.vehDueTaskMapper.updateById(upd);
        }
        return ok;
    }

    /** 量程校验只此一处：提前量空着不行，越出 2~5 日窗口的不行 */
    private boolean dispatchable(BigDecimal amount) {
        return amount != null
                && amount.compareTo(WIN_FROM) >= 0
                && amount.compareTo(WIN_TO) <= 0;
    }
}
