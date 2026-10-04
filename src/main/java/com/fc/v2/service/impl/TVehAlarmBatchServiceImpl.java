package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.conf.V2Config;
import com.fc.v2.mapper.auto.TVehAlarmBatchMapper;
import com.fc.v2.mapper.auto.TVehAlarmRowMapper;
import com.fc.v2.model.auto.TVehAlarmBatch;
import com.fc.v2.model.auto.TVehAlarmRow;
import com.fc.v2.model.custom.VehAlarmReceipt;
import com.fc.v2.service.ITVehAlarmBatchService;
import com.fc.v2.util.StringUtils;

/**
 * 月度报警处置核收册 Service业务层处理（整册进门，一道口子）
 *
 * @author fuce
 * @date 2026-10-02
 */
@Service
public class TVehAlarmBatchServiceImpl extends ServiceImpl<TVehAlarmBatchMapper, TVehAlarmBatch>
        implements ITVehAlarmBatchService {

    /** 处里给每册行数定的顶（配置缺省时的兜底值） */
    private static final int DEFAULT_ROW_CAP = 500;

    @javax.annotation.Resource
    private TVehAlarmRowMapper vehAlarmRowMapper;

    @Autowired
    private V2Config v2Config;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importBatch(String batchNo, Integer sourceKind, Integer reportYear, Integer reportMonth,
                           List<TVehAlarmRow> rows) {
        List<TVehAlarmRow> safeRows = rows == null ? new ArrayList<TVehAlarmRow>() : rows;
        String no = StringUtils.isNotEmpty(batchNo) ? batchNo
                : (safeRows.isEmpty() ? null : safeRows.get(0).getBatchNo());
        if (StringUtils.isEmpty(no)) {
            return 0;
        }
        // 进门头一步先数行：数出来压过那个顶的，整册原样退回，库里一行不留
        int cap = v2Config.getVehAlarmRowCap() != null && v2Config.getVehAlarmRowCap() > 0
                ? v2Config.getVehAlarmRowCap() : DEFAULT_ROW_CAP;
        if (safeRows.size() > cap) {
            return -1;
        }
        // 同一个册码第二遍递来：只与库里已有的行照个面，一行不加、一行不减，
        // 头一遍落成的结果覆不掉；真要去改，先办撤册再重递
        if (selectActiveByBatchNo(no) != null) {
            return 0;
        }
        // 一行也没收着的那种册：只落册头记"空册"，核讫那一说不给它用
        if (safeRows.isEmpty()) {
            this.baseMapper.insert(newBatch(no, sourceKind, reportYear, reportMonth, BATCH_EMPTY));
            return 0;
        }
        // 一册一次一次性进门：册头与整册行同一回落库，不接逐行零着递
        this.baseMapper.insert(newBatch(no, sourceKind, reportYear, reportMonth, BATCH_CHECKING));
        int seq = 0;
        int ok = 0;
        for (TVehAlarmRow r : safeRows) {
            seq++;
            r.setId(null);
            r.setBatchNo(no);
            // 行次不留给人填：进门这一步由系统按落库先后排上，排定了就不再挪
            r.setRowNo(seq);
            r.setLayerFlag(LAYER_CURRENT);
            r.setDelFlag(0);
            String reason = refuseReason(r);
            if (reason != null) {
                // 这一行原路送回，缘故写在行上；同册别行的结果不受它牵动
                r.setStatus(ROW_RETURNED);
                r.setRemark(reason);
            } else {
                r.setStatus(ROW_PENDING);
                ok++;
            }
            vehAlarmRowMapper.insert(r);
        }
        return ok;
    }

    /**
     * 行级进门校验，过不了的原路送回、缘故写在行上；同册别行不受牵动。
     */
    private String refuseReason(TVehAlarmRow r) {
        // 认车只认车辆营运代号：拿车牌去对的、代号念得不全的，一律按没对上办
        if (StringUtils.isEmpty(r.getItemCode()) || r.getItemCode().trim().isEmpty()) {
            return "营运代号念得不全，按没对上办";
        }
        // 两个数得一块儿递上来，缺任一个都不许由谁替它补一个
        if (r.getHoursNum() == null || r.getQty() == null) {
            return "采集条数与未处置条数缺任一个，不许代补";
        }
        // 本行未处置条数高过本行采集条数的，这一行原路送回
        if (r.getQty().compareTo(r.getHoursNum()) > 0) {
            return "本行未处置条数高过本行采集条数";
        }
        return null;
    }

    private TVehAlarmBatch newBatch(String no, Integer sourceKind, Integer reportYear, Integer reportMonth,
                                    int status) {
        TVehAlarmBatch b = new TVehAlarmBatch();
        b.setBatchNo(no);
        b.setSourceKind(sourceKind);
        b.setReportYear(reportYear);
        b.setReportMonth(reportMonth);
        b.setStatus(status);
        b.setDelFlag(0);
        return b;
    }

    @Override
    public TVehAlarmBatch selectActiveByBatchNo(String batchNo) {
        List<TVehAlarmBatch> list = this.baseMapper.selectList(new QueryWrapper<TVehAlarmBatch>()
                .eq("batch_no", batchNo)
                .eq("del_flag", 0)
                .ne("status", BATCH_WITHDRAWN)
                .orderByDesc("id"));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 按册码回查册头（含已撤册，取最近一册） */
    private TVehAlarmBatch selectAnyByBatchNo(String batchNo) {
        List<TVehAlarmBatch> list = this.baseMapper.selectList(new QueryWrapper<TVehAlarmBatch>()
                .eq("batch_no", batchNo)
                .eq("del_flag", 0)
                .orderByDesc("id"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<TVehAlarmBatch> selectTVehAlarmBatchList(Wrapper<TVehAlarmBatch> queryWrapper) {
        return this.baseMapper.selectList(queryWrapper);
    }

    @Override
    public VehAlarmReceipt receipt(String batchNo) {
        VehAlarmReceipt rc = new VehAlarmReceipt();
        rc.setBatchNo(batchNo);
        // 三个数各自回库现点现取，不拿屏上当下显的那一份凑数
        Integer src = vehAlarmRowMapper.selectCount(new QueryWrapper<TVehAlarmRow>()
                .eq("batch_no", batchNo).eq("del_flag", 0));
        Integer back = vehAlarmRowMapper.selectCount(new QueryWrapper<TVehAlarmRow>()
                .eq("batch_no", batchNo).eq("del_flag", 0).eq("status", ROW_RETURNED));
        Integer in = vehAlarmRowMapper.selectCount(new QueryWrapper<TVehAlarmRow>()
                .eq("batch_no", batchNo).eq("del_flag", 0).in("status", ROW_PENDING, ROW_ACCEPTED));
        rc.setSrcCount(src);
        rc.setInCount(in);
        rc.setBackCount(back);
        rc.setEmptyBook(src != null && src == 0);
        TVehAlarmBatch live = selectActiveByBatchNo(batchNo);
        if (live != null) {
            rc.setBatchStatus(live.getStatus());
            if (src != null && src == 0) {
                // 一行也没收着的那种册，回执那一栏写"空册"两个字
                rc.setMark("空册");
            }
        } else {
            TVehAlarmBatch any = selectAnyByBatchNo(batchNo);
            if (any != null) {
                rc.setBatchStatus(any.getStatus());
                rc.setMark("已撤册");
            } else {
                rc.setMark("无此册");
            }
        }
        return rc;
    }

    @Override
    public List<TVehAlarmRow> checkReceipt(String batchNo) {
        // 与回执同一回点算：逐行重核，行上结果不在三选一之内的就是差开的行
        List<TVehAlarmRow> rows = vehAlarmRowMapper.selectList(new QueryWrapper<TVehAlarmRow>()
                .eq("batch_no", batchNo)
                .eq("del_flag", 0)
                .orderByAsc("row_no"));
        List<TVehAlarmRow> diff = new ArrayList<TVehAlarmRow>();
        for (TVehAlarmRow r : rows) {
            Integer st = r.getStatus();
            if (st == null || (st != ROW_PENDING && st != ROW_ACCEPTED && st != ROW_RETURNED)) {
                diff.add(r);
            }
        }
        return diff;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int withdrawBatch(String batchNo) {
        TVehAlarmBatch live = selectActiveByBatchNo(batchNo);
        if (live == null) {
            return 0;
        }
        // 先办撤册这道手续：册头记"已撤册"，册下各行撤出留痕，撤完册码才让重递
        TVehAlarmBatch upd = new TVehAlarmBatch();
        upd.setId(live.getId());
        upd.setStatus(BATCH_WITHDRAWN);
        this.baseMapper.updateById(upd);
        TVehAlarmRow rowUpd = new TVehAlarmRow();
        rowUpd.setDelFlag(1);
        vehAlarmRowMapper.update(rowUpd, new UpdateWrapper<TVehAlarmRow>()
                .eq("batch_no", batchNo)
                .eq("del_flag", 0));
        return 1;
    }

    @Override
    public int finalizeBatch(String batchNo) {
        TVehAlarmBatch live = selectActiveByBatchNo(batchNo);
        if (live == null) {
            return 0;
        }
        Integer src = vehAlarmRowMapper.selectCount(new QueryWrapper<TVehAlarmRow>()
                .eq("batch_no", batchNo).eq("del_flag", 0));
        // 核讫那一说是给有行数的册备的，行数为零不该用它
        if (src == null || src == 0
                || (live.getStatus() != null && live.getStatus() == BATCH_EMPTY)) {
            return -1;
        }
        TVehAlarmBatch upd = new TVehAlarmBatch();
        upd.setId(live.getId());
        upd.setStatus(BATCH_FINALIZED);
        return this.baseMapper.updateById(upd) > 0 ? 1 : 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int archiveStaleRows(int currentYear) {
        // 连着两个报送年度还没着落的旧册，才走得了这条路；下个月的新册走不着
        List<TVehAlarmBatch> oldHeads = this.baseMapper.selectList(new QueryWrapper<TVehAlarmBatch>()
                .eq("del_flag", 0)
                .isNotNull("report_year")
                .le("report_year", currentYear - 2));
        if (oldHeads.isEmpty()) {
            return 0;
        }
        List<String> nos = new ArrayList<String>();
        for (TVehAlarmBatch b : oldHeads) {
            if (StringUtils.isNotEmpty(b.getBatchNo())) {
                nos.add(b.getBatchNo());
            }
        }
        if (nos.isEmpty()) {
            return 0;
        }
        // 只挪结果空着没落（未勾）的行；往期那层照旧翻得着、一条不许少
        TVehAlarmRow upd = new TVehAlarmRow();
        upd.setLayerFlag(LAYER_PAST);
        return vehAlarmRowMapper.update(upd, new UpdateWrapper<TVehAlarmRow>()
                .in("batch_no", nos)
                .eq("status", ROW_PENDING)
                .and(w -> w.eq("layer_flag", LAYER_CURRENT).or().isNull("layer_flag"))
                .eq("del_flag", 0));
    }

    @Override
    public List<TVehAlarmRow> listRows(String batchNo, Integer layerFlag) {
        QueryWrapper<TVehAlarmRow> wrapper = new QueryWrapper<TVehAlarmRow>()
                .eq("batch_no", batchNo)
                .eq("del_flag", 0);
        if (layerFlag != null && layerFlag == LAYER_PAST) {
            wrapper.eq("layer_flag", LAYER_PAST);
        } else {
            wrapper.and(w -> w.eq("layer_flag", LAYER_CURRENT).or().isNull("layer_flag"));
        }
        wrapper.orderByAsc("row_no");
        return vehAlarmRowMapper.selectList(wrapper);
    }
}
