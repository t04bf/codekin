package org.ninestick.codekin.model.common;

import lombok.Getter;

/**
 * 所有业务 HTTP 接口使用的统一响应结构。
 *
 * @author ninestick
 */
@Getter
public class R<T> {

    /** 机器可读结果码；成功固定为 0。 */
    private final String code;

    /** 面向调用方的说明。 */
    private final String message;

    /** 成功数据；失败时为 null。 */
    private final T data;

    private R(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 构造成功响应。 */
    public static <T> R<T> success(T data) {
        return new R<>(CommonCode.SUCCESS.getCode(), CommonCode.SUCCESS.getMessage(), data);
    }

    /** 构造不包含业务数据的失败响应。 */
    public static <T> R<T> failure(Recode recode) {
        return new R<>(recode.getCode(), recode.getMessage(), null);
    }

    /** 保留错误码，同时使用业务异常提供的消息。 */
    public static <T> R<T> failure(Recode recode, String message) {
        return new R<>(recode.getCode(), message, null);
    }
}
