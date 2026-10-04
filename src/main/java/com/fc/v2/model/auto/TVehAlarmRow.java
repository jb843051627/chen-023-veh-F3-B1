package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 报警处置核收行对象 t_veh_alarm_row
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_veh_alarm_row")
@ApiModel(value = "TVehAlarmRow", description = "报警处置核收行")
public class TVehAlarmRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 月度报警处置核收册码 */
    @TableField("batch_no")
    @ApiModelProperty(value = "月度报警处置核收册码")
    private String batchNo;

    /** 原报行次 */
    @TableField("row_no")
    @ApiModelProperty(value = "原报行次")
    private Integer rowNo;

    /** 被对上的营运车辆代号 */
    @TableField("item_code")
    @ApiModelProperty(value = "被对上的营运车辆代号")
    private String itemCode;

    /** 本行仍未处置条数 */
    @TableField("qty")
    @ApiModelProperty(value = "本行仍未处置条数")
    private BigDecimal qty;

    /** 本行采集报警条数 */
    @TableField("hours_num")
    @ApiModelProperty(value = "本行采集报警条数")
    private BigDecimal hoursNum;

    /** 计量单位口径（条） */
    @TableField("unit_kind")
    @ApiModelProperty(value = "计量单位口径（条）")
    private String unitKind;

    /** 行落地情形 0未勾 1已收下 2已退回 */
    @TableField("status")
    @ApiModelProperty(value = "行落地情形 0未勾 1已收下 2已退回")
    private Integer status;

    /** 所在层 0现行 1往期 */
    @TableField("layer_flag")
    @ApiModelProperty(value = "所在层 0现行 1往期")
    private Integer layerFlag;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getHoursNum() {
        return hoursNum;
    }

    public void setHoursNum(BigDecimal hoursNum) {
        this.hoursNum = hoursNum;
    }

    public String getUnitKind() {
        return unitKind;
    }

    public void setUnitKind(String unitKind) {
        this.unitKind = unitKind;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getLayerFlag() {
        return layerFlag;
    }

    public void setLayerFlag(Integer layerFlag) {
        this.layerFlag = layerFlag;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
