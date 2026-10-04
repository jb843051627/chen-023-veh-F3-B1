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
import java.util.Date;

/**
 * 车辆联网清单页对象 t_veh_bind_page
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_veh_bind_page")
@ApiModel(value = "TVehBindPage", description = "车辆联网清单页")
public class TVehBindPage implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 车辆联网清单页代号（清单代号，系统排号，本子自带不认） */
    @TableField("bill_no")
    @ApiModelProperty(value = "车辆联网清单页代号")
    private String billNo;

    /** 随页签认码（系统排号，本子自带不认） */
    @TableField("sign_no")
    @ApiModelProperty(value = "随页签认码")
    private String signNo;

    /** 车辆营运代号 */
    @TableField("veh_code")
    @ApiModelProperty(value = "车辆营运代号")
    private String vehCode;

    /** 页所在层级序 0县域 1市域 2省域 */
    @TableField("node_no")
    @ApiModelProperty(value = "页所在层级序 0县域 1市域 2省域")
    private Integer nodeNo;

    /** 所属运输业户 */
    @TableField("site_id")
    @ApiModelProperty(value = "所属运输业户")
    private Integer siteId;

    /** 所属业户代号 */
    @TableField("site_no")
    @ApiModelProperty(value = "所属业户代号")
    private String siteNo;

    /** 应联项数 */
    @TableField("should_count")
    @ApiModelProperty(value = "应联项数")
    private Integer shouldCount;

    /** 已联项数 */
    @TableField("done_count")
    @ApiModelProperty(value = "已联项数")
    private Integer doneCount;

    /** 还差几项 */
    @TableField("lack_count")
    @ApiModelProperty(value = "还差几项")
    private Integer lackCount;

    /** 随页交来的联网要件 */
    @TableField("content")
    @ApiModelProperty(value = "随页交来的联网要件")
    private String content;

    /** 联网进展 0新录 1已联齐 2缺项 */
    @TableField("status")
    @ApiModelProperty(value = "联网进展 0新录 1已联齐 2缺项")
    private Integer status;

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

    /** 业户全称（join 总名录回显，不落本页表） */
    @TableField(exist = false)
    @ApiModelProperty(value = "业户全称")
    private String siteName;

    /** 业户类别（join 总名录回显，不落本页表） */
    @TableField(exist = false)
    @ApiModelProperty(value = "业户类别")
    private String siteType;

    /** 属地那一路（join 总名录回显，不落本页表） */
    @TableField(exist = false)
    @ApiModelProperty(value = "属地那一路")
    private String roadName;

    /** 本屏挂页车数（开屏同回点算的窗口计数，不落本页表） */
    @TableField(exist = false)
    @ApiModelProperty(value = "本屏挂页车数")
    private Integer boardTotal;

    /** 本屏已联齐车数（开屏同回点算的窗口计数，不落本页表） */
    @TableField(exist = false)
    @ApiModelProperty(value = "本屏已联齐车数")
    private Integer boardLinked;

    /** 本屏缺项车数（开屏同回点算的窗口计数，不落本页表） */
    @TableField(exist = false)
    @ApiModelProperty(value = "本屏缺项车数")
    private Integer boardLack;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public String getSignNo() {
        return signNo;
    }

    public void setSignNo(String signNo) {
        this.signNo = signNo;
    }

    public String getVehCode() {
        return vehCode;
    }

    public void setVehCode(String vehCode) {
        this.vehCode = vehCode;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
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

    public Integer getShouldCount() {
        return shouldCount;
    }

    public void setShouldCount(Integer shouldCount) {
        this.shouldCount = shouldCount;
    }

    public Integer getDoneCount() {
        return doneCount;
    }

    public void setDoneCount(Integer doneCount) {
        this.doneCount = doneCount;
    }

    public Integer getLackCount() {
        return lackCount;
    }

    public void setLackCount(Integer lackCount) {
        this.lackCount = lackCount;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public String getSiteType() {
        return siteType;
    }

    public void setSiteType(String siteType) {
        this.siteType = siteType;
    }

    public String getRoadName() {
        return roadName;
    }

    public void setRoadName(String roadName) {
        this.roadName = roadName;
    }

    public Integer getBoardTotal() {
        return boardTotal;
    }

    public void setBoardTotal(Integer boardTotal) {
        this.boardTotal = boardTotal;
    }

    public Integer getBoardLinked() {
        return boardLinked;
    }

    public void setBoardLinked(Integer boardLinked) {
        this.boardLinked = boardLinked;
    }

    public Integer getBoardLack() {
        return boardLack;
    }

    public void setBoardLack(Integer boardLack) {
        this.boardLack = boardLack;
    }
}
