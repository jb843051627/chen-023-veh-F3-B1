package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TVehAlarmRowMapper;
import com.fc.v2.model.auto.TVehAlarmRow;
import com.fc.v2.service.ITVehAlarmBatchService;
import com.fc.v2.service.ITVehAlarmRowService;
import com.fc.v2.util.StringUtils;

/**
 * 报警处置核收行 Service业务层处理（batch-process 形状：整批提交）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TVehAlarmRowServiceImpl implements ITVehAlarmRowService {

    @javax.annotation.Resource
    private TVehAlarmRowMapper vehAlarmRowMapper;

    @javax.annotation.Resource
    private ITVehAlarmBatchService vehAlarmBatchService;

    @Override
    public TVehAlarmRow selectTVehAlarmRowById(Long id) {
        return this.vehAlarmRowMapper.selectById(id);
    }

    @Override
    public int submitBatch(String batchNo, List<TVehAlarmRow> rows) {
        // 机构自录与各市转递算两条来路，两条来路走同一道门：整册导入口径全在册头服务
        return vehAlarmBatchService.importBatch(batchNo, null, null, null, rows);
    }

    @Override
    public List<TVehAlarmRow> listErrors(String batchNo) {
        return this.vehAlarmRowMapper.selectList(new QueryWrapper<TVehAlarmRow>()
                .eq("batch_no", batchNo)
                .eq("status", ITVehAlarmBatchService.ROW_RETURNED)
                .eq("del_flag", 0)
                .orderByAsc("row_no"));
    }

    @Override
    public int checkRow(Long id, Integer status, String remark) {
        if (id == null || status == null) {
            return 0;
        }
        // 行上的结果只许一个说法：收下、退回二选一才落得下笔
        if (status != ITVehAlarmBatchService.ROW_ACCEPTED && status != ITVehAlarmBatchService.ROW_RETURNED) {
            return 0;
        }
        TVehAlarmRow row = this.vehAlarmRowMapper.selectById(id);
        if (row == null || row.getDelFlag() == null || row.getDelFlag() != 0) {
            return 0;
        }
        // 挪到往期那一层的行不再勾
        if (row.getLayerFlag() != null && row.getLayerFlag() == ITVehAlarmBatchService.LAYER_PAST) {
            return 0;
        }
        // 只许从"未勾"落笔：已落成的结果谁也不许覆，要改先办撤册再重递
        if (row.getStatus() == null || row.getStatus() != ITVehAlarmBatchService.ROW_PENDING) {
            return 0;
        }
        // 退回须把缘故写在行上
        if (status == ITVehAlarmBatchService.ROW_RETURNED && StringUtils.isEmpty(remark)) {
            return 0;
        }
        TVehAlarmRow upd = new TVehAlarmRow();
        upd.setId(id);
        upd.setStatus(status);
        if (StringUtils.isNotEmpty(remark)) {
            upd.setRemark(remark);
        }
        return this.vehAlarmRowMapper.updateById(upd);
    }
}
