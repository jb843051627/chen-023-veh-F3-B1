package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;

/**
 * 核收册回执（三个数各自回库现点现取，不拿屏上当下显的凑数）
 *
 * @author fuce
 * @date 2026-10-02
 */
@ApiModel(value = "VehAlarmReceipt", description = "核收册回执")
public class VehAlarmReceipt implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 月度报警处置核收册码 */
    @ApiModelProperty(value = "月度报警处置核收册码")
    private String batchNo;

    /** 册情形 0在核 1核讫 2空册 3已撤册 */
    @ApiModelProperty(value = "册情形 0在核 1核讫 2空册 3已撤册")
    private Integer batchStatus;

    /** 册上原有几行 */
    @ApiModelProperty(value = "册上原有几行")
    private Integer srcCount;

    /** 落进库里几行 */
    @ApiModelProperty(value = "落进库里几行")
    private Integer inCount;

    /** 原样送回几行 */
    @ApiModelProperty(value = "原样送回几行")
    private Integer backCount;

    /** 是否空册（一行也没收着） */
    @ApiModelProperty(value = "是否空册（一行也没收着）")
    private Boolean emptyBook;

    /** 回执批注：空册/已撤册/无此册 */
    @ApiModelProperty(value = "回执批注：空册/已撤册/无此册")
    private String mark;

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Integer getBatchStatus() {
        return batchStatus;
    }

    public void setBatchStatus(Integer batchStatus) {
        this.batchStatus = batchStatus;
    }

    public Integer getSrcCount() {
        return srcCount;
    }

    public void setSrcCount(Integer srcCount) {
        this.srcCount = srcCount;
    }

    public Integer getInCount() {
        return inCount;
    }

    public void setInCount(Integer inCount) {
        this.inCount = inCount;
    }

    public Integer getBackCount() {
        return backCount;
    }

    public void setBackCount(Integer backCount) {
        this.backCount = backCount;
    }

    public Boolean getEmptyBook() {
        return emptyBook;
    }

    public void setEmptyBook(Boolean emptyBook) {
        this.emptyBook = emptyBook;
    }

    public String getMark() {
        return mark;
    }

    public void setMark(String mark) {
        this.mark = mark;
    }
}
