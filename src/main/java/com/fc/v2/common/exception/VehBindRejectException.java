package com.fc.v2.common.exception;

/**
 * 联网清单页落库退回：必须点明毛病出在哪一格。
 * 接口层捕到后照原话退回，不许只回一句"数不对劲"。
 *
 * @author fuce
 */
public class VehBindRejectException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** 出毛病的那一格（字段名） */
    private final String field;

    public VehBindRejectException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
