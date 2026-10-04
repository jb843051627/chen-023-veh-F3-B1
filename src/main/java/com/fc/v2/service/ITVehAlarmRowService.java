package com.fc.v2.service;

import java.util.List;

import com.fc.v2.model.auto.TVehAlarmRow;

/**
 * 报警处置核收行 Service接口（batch-process 形状：整批提交，无单条增删改）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITVehAlarmRowService {

    /** 按主键回查单行 */
    TVehAlarmRow selectTVehAlarmRowById(Long id);

    /**
     * 整批提交：与册头服务同一道门，逐行校验，合法的落库候勾、非法的原路送回并把缘故写在行上
     * （行次由系统按落库先后排上），返回**落库候勾行数**。
     * 同一册码第二遍递来只照面、一行不加一行不减；行数超过单批上限返回 -1 且整批不入库；空批次返回 0。
     */
    int submitBatch(String batchNo, List<TVehAlarmRow> rows);

    /** 该批次的送回行明细（按行次升序） */
    List<TVehAlarmRow> listErrors(String batchNo);

    /**
     * 行勾选：只许从"未勾"落笔，勾成"收下"或"退回"（退回须把缘故写在行上）；
     * 已落成的结果谁也不许覆，往期层的行不再勾。返回 1 勾成，否则 0。
     */
    int checkRow(Long id, Integer status, String remark);
}
