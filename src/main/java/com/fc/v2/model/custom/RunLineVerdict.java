package com.fc.v2.model.custom;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 界次归层结论（四问只由服务层一处作答，屏上不另摆第二套口径）。
 * 归得上层时带界次代号与起用那一日；一条界都够不上时这两栏一并留空，
 * 结论栏写"无可用界"，不许抄旁边那条的数字凑答复。
 *
 * @author fuce
 * @date 2026-10-04
 */
@ApiModel(value = "RunLineVerdict", description = "界次归层结论")
public class RunLineVerdict implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 结论栏：归得上层写层名（提示/严管/立案），一条界都够不上写"无可用界" */
    @ApiModelProperty(value = "结论（提示/严管/立案/无可用界）")
    private String levelText;

    /** 层序：1提示 2严管 3立案；无可用界为空 */
    @ApiModelProperty(value = "层序 1提示 2严管 3立案；无可用界为空")
    private Integer level;

    /** 说话的那一条界次代号；无可用界时留空，不许抄旁边那条 */
    @ApiModelProperty(value = "说话的界次代号；无可用界留空")
    private String ruleCode;

    /** 说话的那一条的起用那一日；无可用界时留空 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "起用那一日；无可用界留空")
    private Date effStart;

    /** 车上报数折到界册那一头之后的数（取数与比数同一把舍法），原样带回留痕 */
    @ApiModelProperty(value = "折到界册那一头之后的数")
    private BigDecimal converted;

    public RunLineVerdict() {
    }

    public RunLineVerdict(String levelText, Integer level, String ruleCode, Date effStart, BigDecimal converted) {
        this.levelText = levelText;
        this.level = level;
        this.ruleCode = ruleCode;
        this.effStart = effStart;
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

    public BigDecimal getConverted() {
        return converted;
    }

    public void setConverted(BigDecimal converted) {
        this.converted = converted;
    }
}
