package com.fc.v2.service.impl;

import java.util.Calendar;
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
 * 开口窗口只由 {@link #listDue} 一处算：到了"应开口止点"往前最多 amount 日那一时刻就可开口，
 * 逾期照催；{@link #runOnce} 直接照这份名单派，两处不许各筛各的。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TVehDueTaskServiceImpl implements ITVehDueTaskService {

    /** 条目情形：待派（存量空着的也按待派认，两处同一说法） */
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

    @Override
    public List<TVehDueTask> listDue(Date at) {
        List<TVehDueTask> due = new java.util.ArrayList<TVehDueTask>();
        if (at == null) {
            // 问话时刻都没给：还一份空名单作兜底，不许把异常甩给调用方
            return due;
        }
        List<TVehDueTask> all = this.vehDueTaskMapper.selectList(
                new QueryWrapper<TVehDueTask>().eq("del_flag", 0));
        for (TVehDueTask r : all) {
            if (isDispatchable(r, at)) {
                due.add(r);
            }
        }
        return due;
    }

    @Override
    public int runOnce(Date at) {
        // 直接照开口名单派，不另筛第二遍：名单与派出口径同源，结存才对得齐
        List<TVehDueTask> due = listDue(at);
        int ok = 0;
        for (TVehDueTask r : due) {
            try {
                TVehDueTask upd = new TVehDueTask();
                upd.setId(r.getId());
                upd.setStatus(STATUS_DONE);
                if (this.vehDueTaskMapper.updateById(upd) > 0) {
                    ok++;
                }
            } catch (Exception e) {
                // 单条派砸了不牵动别条：这一条留在待派，下一轮再派
            }
        }
        return ok;
    }

    /**
     * 一条这一轮派不派得动，只此一处判定，listDue 与 runOnce 都照它：
     * 未派过、应开口止点不缺、问话时刻已到开口点（止点往前最多 amount 日，含开口那一刻）。
     * 判据缺一格/念不成的，跳过这一条给兜底，不许抛异常出去。
     */
    private boolean isDispatchable(TVehDueTask r, Date at) {
        if (r.getStatus() != null && r.getStatus() != STATUS_WAIT) {
            // 已派出、已分不出的不再派；只有待派（含存量空着）才开口
            return false;
        }
        Date stop = r.getDueAt();
        if (stop == null) {
            return false;
        }
        int aheadDays = 0;
        if (r.getAmount() != null) {
            // "最可提前几日"不该为负；念成负数的按零兜底（当日开口），不替它抛异常
            int v = r.getAmount().intValue();
            if (v > 0) {
                aheadDays = v;
            }
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(stop);
        cal.add(Calendar.DAY_OF_MONTH, -aheadDays);
        Date openAt = cal.getTime();
        // 已到开口点（含那一刻）就可派；过了止点逾期的也照催
        return ts(at).compareTo(ts(openAt)) >= 0;
    }
}
