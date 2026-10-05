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
 * 运行界次归层归档对象 t_veh_run_verdict。
 *
 * 每一次裁决判完即落这一册：报的什么数、折完是多少、归的哪一层、哪条界说的话、
 * 问的是哪一刻，原样留底。日后追溯只翻这一份，不许事后拿新界重算顶替。
 *
 * @author fuce
 * @date 2026-10-04
 */
@TableName("t_veh_run_verdict")
@ApiModel(value = "TVehRunVerdict", description = "运行界次归层归档")
public class TVehRunVerdict implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所量类别（speed 车速 / duration 连续驾驶时长） */
    @TableField("measure_kind")
    @ApiModelProperty(value = "所量类别")
    private String measureKind;

    /** 车上报回来的数（车上端口径：车速 m/s、时长分钟） */
    @TableField("raw_value")
    @ApiModelProperty(value = "车上报回来的数（车上端口径）")
    private BigDecimal rawValue;

    /** 折到界册那一头之后的数（取数与比数同一把舍法） */
    @TableField("converted")
    @ApiModelProperty(value = "折到界册那一头之后的数")
    private BigDecimal converted;

    /** 结论栏（提示/严管/立案/未到起算/无可用界） */
    @TableField("level_text")
    @ApiModelProperty(value = "结论栏")
    private String levelText;

    /** 层序 1提示 2严管 3立案；无层为空 */
    @TableField("level")
    @ApiModelProperty(value = "层序 1提示 2严管 3立案；无层为空")
    private Integer level;

    /** 说话的那一条界次代号；无可用界留空 */
    @TableField("rule_code")
    @ApiModelProperty(value = "说话的界次代号；无可用界留空")
    private String ruleCode;

    /** 说话的那一条的起用那一日；无可用界留空 */
    @TableField("eff_start")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "说话界起用那一日；无可用界留空")
    private Date effStart;

    /** 问话的那一刻（补判旧账就是旧账自己的那一刻） */
    @TableField("asked_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "问话的那一刻")
    private Date askedAt;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间（落册那一刻） */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间（落册那一刻）")
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

    public String getMeasureKind() {
        return measureKind;
    }

    public void setMeasureKind(String measureKind) {
        this.measureKind = measureKind;
    }

    public BigDecimal getRawValue() {
        return rawValue;
    }

    public void setRawValue(BigDecimal rawValue) {
        this.rawValue = rawValue;
    }

    public BigDecimal getConverted() {
        return converted;
    }

    public void setConverted(BigDecimal converted) {
        this.converted = converted;
    }

    public String getLevelText() {
        return levelText;
    }

    public void setLevelText(String levelText) {
        this.levelText = levelText;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public Date getEffStart() {
        return effStart;
    }

    public void setEffStart(Date effStart) {
        this.effStart = effStart;
    }

    public Date getAskedAt() {
        return askedAt;
    }

    public void setAskedAt(Date askedAt) {
        this.askedAt = askedAt;
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
