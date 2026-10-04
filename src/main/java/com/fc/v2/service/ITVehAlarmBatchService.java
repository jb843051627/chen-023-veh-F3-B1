package com.fc.v2.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TVehAlarmBatch;
import com.fc.v2.model.auto.TVehAlarmRow;
import com.fc.v2.model.custom.VehAlarmReceipt;

/**
 * 月度报警处置核收册 Service接口（整册进门，一道口子）
 *
 * @author fuce
 * @date 2026-10-02
 */
public interface ITVehAlarmBatchService {

    /** 行结果：未勾 */
    int ROW_PENDING = 0;
    /** 行结果：收下 */
    int ROW_ACCEPTED = 1;
    /** 行结果：退回 */
    int ROW_RETURNED = 2;

    /** 册情形：在核 */
    int BATCH_CHECKING = 0;
    /** 册情形：核讫 */
    int BATCH_FINALIZED = 1;
    /** 册情形：空册 */
    int BATCH_EMPTY = 2;
    /** 册情形：已撤册 */
    int BATCH_WITHDRAWN = 3;

    /** 所在层：现行 */
    int LAYER_CURRENT = 0;
    /** 所在层：往期 */
    int LAYER_PAST = 1;

    /**
     * 整册导入（机构自录与各市转递同走这一道门，一册一次一次性进门，不接逐行零着递）。
     * 进门头一步先数行：行数压过处里定的顶，整册原样退回，库里一行不留，返回 -1；
     * 一行没有的按空册落册头、回执写"空册"，返回 0；
     * 同一个册码第二遍递来只与库里已有的行照个面，一行不加、一行不减，返回 0；
     * 正常进门返回落库（未勾）行数。
     */
    int importBatch(String batchNo, Integer sourceKind, Integer reportYear, Integer reportMonth, List<TVehAlarmRow> rows);

    /** 按册码回查在办册头（已撤册的不算） */
    TVehAlarmBatch selectActiveByBatchNo(String batchNo);

    /** 册列表（分页用） */
    List<TVehAlarmBatch> selectTVehAlarmBatchList(Wrapper<TVehAlarmBatch> queryWrapper);

    /**
     * 回执：册上原有几行、落进库里几行、原样送回几行。
     * 三个数各自回库现点现取；送回的加上落库的接得平原有的行数；空册批"空册"两个字。
     */
    VehAlarmReceipt receipt(String batchNo);

    /**
     * 回执一致性核对：与回执同一回点算逐行重核，
     * 行上结果不在"未勾/收下/退回"三选一之内的，就是差开的行，当场报出来。
     */
    List<TVehAlarmRow> checkReceipt(String batchNo);

    /**
     * 撤册：要改册先办这道手续，册头记"已撤册"、册下各行撤出留痕，撤完册码才让重递。
     */
    int withdrawBatch(String batchNo);

    /**
     * 核讫：只给有行数的册发；空册（一行也没收着的）不许发核讫。
     * 返回 1 讫成；0 无此在办册；-1 空册不发核讫。
     */
    int finalizeBatch(String batchNo);

    /**
     * 往期迁移：结果空着没落（未勾）的行，连着两个报送年度还没着落的，
     * 挪到往期那一层，现行名单上不摆；往期照旧翻得着、一条不许少。
     * 这条路只朝旧册开，新册走不着。返回挪过去的行数。
     */
    int archiveStaleRows(int currentYear);

    /** 册下各行（layerFlag 不传看现行，传 1 翻往期），按行次升序 */
    List<TVehAlarmRow> listRows(String batchNo, Integer layerFlag);
}
