package com.fc.v2.model.auto;

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
 * 运行界次裁决留痕对象 t_veh_run_line_verdict。
 *
 * 运行界次判完即归档在册：说话的那条界连同分界数、起用日、交班次序一并快照落进来，
 * 日后界册改了、界交班了，追溯都以归档的这一份为准，不许拿最新版阈值重算旧账。
 *
 * @author fuce
 * @date 2026-10-05
 */
@TableName("t_veh_run_line_verdict")
@ApiModel(value = "TVehRunLineVerdictRecord", description = "运行界次裁决留痕")
public class TVehRunLineVerdictRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所量类别 speed 车速 / duration 连续驾驶时长 */
    @TableField("measure_kind")
    @ApiModelProperty(value = "所量类别 speed/duration")
    private String measureKind;

    /** 车上报回来的原值（车速 m/s、时长分钟） */
    @TableField("raw_value")
    @ApiModelProperty(value = "车上报回来的原值")
    private BigDecimal rawValue;

    /** 折到界册那一头之后的数 */
    @TableField("converted_value")
    @ApiModelProperty(value = "折到界册那一头之后的数")
    private BigDecimal convertedValue;

    /** 问话的那一刻（补判旧账传旧账自己的那一刻） */
    @TableField("ask_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "问话的那一刻")
    private Date askAt;

    /** 说话的界次代号（无可用界/未到起算/超量程时留空） */
    @TableField("rule_code")
    @ApiModelProperty(value = "说话的界次代号")
    private String ruleCode;

    /** 说话的界名（裁决当时的快照） */
    @TableField("rule_name")
    @ApiModelProperty(value = "说话的界名快照")
    private String ruleName;

    /** 说话界的起用那一日（快照） */
    @TableField("eff_start")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "起用那一日快照")
    private Date effStart;

    /** 起算数值快照 */
    @TableField("th1_max")
    @ApiModelProperty(value = "起算数值快照")
    private BigDecimal th1Max;

    /** 够线数值快照 */
    @TableField("th2_max")
    @ApiModelProperty(value = "够线数值快照")
    private BigDecimal th2Max;

    /** 封顶数值快照 */
    @TableField("th3_max")
    @ApiModelProperty(value = "封顶数值快照")
    private BigDecimal th3Max;

    /** 界册单位快照 */
    @TableField("unit_kind")
    @ApiModelProperty(value = "界册单位快照")
    private String unitKind;

    /** 交班次序快照 */
    @TableField("priority")
    @ApiModelProperty(value = "交班次序快照")
    private Integer priority;

    /** 层序 1提示 2严管 3立案；无可用界/未到起算/超量程为空 */
    @TableField("level")
    @ApiModelProperty(value = "层序 1提示 2严管 3立案；无层为空")
    private Integer level;

    /** 结论栏 */
    @TableField("level_text")
    @ApiModelProperty(value = "结论（提示/严管/立案/未到起算/无可用界/超出量程）")
    private String levelText;

    /** 判完归档在册的时刻 */
    @TableField("archived_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "归档时刻")
    private Date archivedAt;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

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

    public BigDecimal getConvertedValue() {
        return convertedValue;
    }

    public void setConvertedValue(BigDecimal convertedValue) {
        this.convertedValue = convertedValue;
    }

    public Date getAskAt() {
        return askAt;
    }

    public void setAskAt(Date askAt) {
        this.askAt = askAt;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public Date getEffStart() {
        return effStart;
    }

    public void setEffStart(Date effStart) {
        this.effStart = effStart;
    }

    public BigDecimal getTh1Max() {
        return th1Max;
    }

    public void setTh1Max(BigDecimal th1Max) {
        this.th1Max = th1Max;
    }

    public BigDecimal getTh2Max() {
        return th2Max;
    }

    public void setTh2Max(BigDecimal th2Max) {
        this.th2Max = th2Max;
    }

    public BigDecimal getTh3Max() {
        return th3Max;
    }

    public void setTh3Max(BigDecimal th3Max) {
        this.th3Max = th3Max;
    }

    public String getUnitKind() {
        return unitKind;
    }

    public void setUnitKind(String unitKind) {
        this.unitKind = unitKind;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public String getLevelText() {
        return levelText;
    }

    public void setLevelText(String levelText) {
        this.levelText = levelText;
    }

    public Date getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Date archivedAt) {
        this.archivedAt = archivedAt;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }
}
