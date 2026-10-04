package com.fc.v2.common.domain;

/**
 * 分页结果的封装
 *
 * @author zhaonz
 * @Date 20210815
 */
public class ResultTable {

    /**
     * 状态码
     */
    private Integer code;

    /**
     * 提示消息
     */
    private String msg;

    /**
     * 消息总量
     */
    private Long count;

    /**
     * 数据对象
     */
    private Object data;

    /**
     * 联网清单页头三栏：挂页车数
     */
    private Integer bindTotal;

    /**
     * 联网清单页头三栏：已联齐
     */
    private Integer bindLinked;

    /**
     * 联网清单页头三栏：缺项
     */
    private Integer bindLack;

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public Integer getBindTotal() {
        return bindTotal;
    }

    public void setBindTotal(Integer bindTotal) {
        this.bindTotal = bindTotal;
    }

    public Integer getBindLinked() {
        return bindLinked;
    }

    public void setBindLinked(Integer bindLinked) {
        this.bindLinked = bindLinked;
    }

    public Integer getBindLack() {
        return bindLack;
    }

    public void setBindLack(Integer bindLack) {
        this.bindLack = bindLack;
    }

    /**
     * 构 建
     *
     * @param count
     * @param data
     * @return
     */
    public static ResultTable pageTable(long count, Object data) {
        ResultTable resultTable = new ResultTable();
        resultTable.setData(data);
        resultTable.setCode(0);
        resultTable.setCount(count);
        return resultTable;
    }

    /**
     * @param data
     * @return
     */
    public static ResultTable dataTable(Object data) {
        ResultTable resultTable = new ResultTable();
        resultTable.setData(data);
        resultTable.setCode(0);
        return resultTable;
    }
}
