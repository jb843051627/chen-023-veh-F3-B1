package com.fc.v2.model.custom;

import java.io.Serializable;

/**
 * 联网清单页立页/改页表单。
 * 应联、已联两格以原文收，先过格式再交业务服务层那唯一一条写入方法：
 * 空着、带零头的在进门这一步就点明格子退回，不放行进业务层凑数。
 *
 * @author fuce
 */
public class VehBindForm implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 改页时给，立页不给 */
    private Long id;

    /** 车辆营运代号 */
    private String vehCode;

    /** 所属运输业户（总名录 id） */
    private Integer siteId;

    /** 所属业户代号（随表单照名录核，不统一名录的起不来） */
    private String siteNo;

    /** 页所在层级序 0县域 1市域 2省域 */
    private Integer nodeNo;

    /** 应联项数原文（整数才可入库，空/零头当场退回） */
    private String shouldCountRaw;

    /** 已联项数原文（整数才可入库，空/零头当场退回） */
    private String doneCountRaw;

    /** 随页交来的联网要件 */
    private String content;

    /** 备注 */
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVehCode() {
        return vehCode;
    }

    public void setVehCode(String vehCode) {
        this.vehCode = vehCode;
    }

    public Integer getSiteId() {
        return siteId;
    }

    public void setSiteId(Integer siteId) {
        this.siteId = siteId;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public String getShouldCountRaw() {
        return shouldCountRaw;
    }

    public void setShouldCountRaw(String shouldCountRaw) {
        this.shouldCountRaw = shouldCountRaw;
    }

    public String getDoneCountRaw() {
        return doneCountRaw;
    }

    public void setDoneCountRaw(String doneCountRaw) {
        this.doneCountRaw = doneCountRaw;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
