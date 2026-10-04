package com.fc.v2.model.custom;

import com.fc.v2.model.auto.TVehAlarmRow;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.List;

/**
 * 整册导入报文（一册一次一次性进门，不接逐行零着递）
 *
 * @author fuce
 * @date 2026-10-02
 */
@ApiModel(value = "VehAlarmImport", description = "整册导入报文")
public class VehAlarmImport implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 月度报警处置核收册码 */
    @ApiModelProperty(value = "月度报警处置核收册码")
    private String batchNo;

    /** 来路 0机构自录 1市里转递（两条来路走同一道门） */
    @ApiModelProperty(value = "来路 0机构自录 1市里转递")
    private Integer sourceKind;

    /** 报送年度 */
    @ApiModelProperty(value = "报送年度")
    private Integer reportYear;

    /** 报送月度 */
    @ApiModelProperty(value = "报送月度")
    private Integer reportMonth;

    /** 册下各行（行次序以机构月度文件为准，行次由系统进门时排） */
    @ApiModelProperty(value = "册下各行")
    private List<TVehAlarmRow> rows;

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Integer getSourceKind() {
        return sourceKind;
    }

    public void setSourceKind(Integer sourceKind) {
        this.sourceKind = sourceKind;
    }

    public Integer getReportYear() {
        return reportYear;
    }

    public void setReportYear(Integer reportYear) {
        this.reportYear = reportYear;
    }

    public Integer getReportMonth() {
        return reportMonth;
    }

    public void setReportMonth(Integer reportMonth) {
        this.reportMonth = reportMonth;
    }

    public List<TVehAlarmRow> getRows() {
        return rows;
    }

    public void setRows(List<TVehAlarmRow> rows) {
        this.rows = rows;
    }
}
