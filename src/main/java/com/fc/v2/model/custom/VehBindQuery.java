package com.fc.v2.model.custom;

import java.io.Serializable;

/**
 * 联网清单屏查条件。
 * 找页给三个头——清单代号、车辆营运代号、业户类别：整段对上的算找着，只对上开头那几笔也算（前缀匹配）。
 * 属地一路（省—市—县前缀）与层级一并收口：县岗不见邻县，市域不掺别市。
 *
 * @author fuce
 */
public class VehBindQuery implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 页所在层级序 0县域 1市域 2省域（必给，各层只管自家那一片） */
    private Integer nodeNo;

    /** 属地那一路前缀：县给"省—市—县"整段，市给"省—市—"，省给"省—" */
    private String roadPrefix;

    /** 查找头：清单代号（前缀匹配） */
    private String billNo;

    /** 查找头：车辆营运代号（前缀匹配） */
    private String vehCode;

    /** 查找头：业户类别（前缀匹配，三类各排各的序） */
    private String siteType;

    /** 页码（从 1 起，前端 layui 以 page 递） */
    private Integer page;

    /** 每页几条（处里定；不传取处里定的兜底数，前端 layui 以 limit 递） */
    private Integer limit;

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public String getRoadPrefix() {
        return roadPrefix;
    }

    public void setRoadPrefix(String roadPrefix) {
        this.roadPrefix = roadPrefix;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public String getVehCode() {
        return vehCode;
    }

    public void setVehCode(String vehCode) {
        this.vehCode = vehCode;
    }

    public String getSiteType() {
        return siteType;
    }

    public void setSiteType(String siteType) {
        this.siteType = siteType;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
