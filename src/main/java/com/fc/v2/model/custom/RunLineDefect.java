package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;

/**
 * 界册逐条体检挑出的一条毛病。
 * 不能只回一句"数值不对劲"：缺数要指名哪一格空着，次序不对要指名哪一格没越过前头那一格；
 * 屏上挂着界次代号、册里那一刻却请不出在位界的，同样一条条指出来。
 *
 * @author fuce
 * @date 2026-10-04
 */
@ApiModel(value = "RunLineDefect", description = "界册毛病（指名到格）")
public class RunLineDefect implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 界次代号（屏上悬空那类毛病只有这一栏有值） */
    @ApiModelProperty(value = "界次代号")
    private String ruleCode;

    /** 界名 */
    @ApiModelProperty(value = "界名")
    private String ruleName;

    /** 毛病归类：threshold_missing（某格空着）/threshold_order（没越过前头那一格）/screen_dangling（屏挂悬空） */
    @ApiModelProperty(value = "毛病归类 threshold_missing/threshold_order/screen_dangling")
    private String kind;

    /** 指名道姓的说法：哪一格空着、哪一格没越过前头那一格 */
    @ApiModelProperty(value = "毛病说法（指名到格）")
    private String detail;

    public RunLineDefect() {
    }

    public RunLineDefect(String ruleCode, String ruleName, String kind, String detail) {
        this.ruleCode = ruleCode;
        this.ruleName = ruleName;
        this.kind = kind;
        this.detail = detail;
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

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
