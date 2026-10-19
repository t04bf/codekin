package org.ninestick.codekin.model.common;

/**
 * 不属于单一业务域的基础结果码。
 *
 * @author ninestick
 */
public enum CommonCode implements Recode {
    SUCCESS("0", "成功"),
    INVALID_ARGUMENT("COMMON_INVALID_ARGUMENT", "请求参数错误"),
    UNAUTHORIZED("COMMON_UNAUTHORIZED", "登录状态无效或已过期"),
    NOT_FOUND("COMMON_NOT_FOUND", "接口不存在"),
    METHOD_NOT_ALLOWED("COMMON_METHOD_NOT_ALLOWED", "请求方法不支持"),
    INTERNAL_ERROR("COMMON_INTERNAL_ERROR", "系统暂时不可用");

    private final String code;
    private final String message;

    CommonCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
