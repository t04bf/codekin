package org.ninestick.codekin.sms;

import org.ninestick.codekin.model.common.Recode;

/**
 * 短信基础设施的业务错误码。
 *
 * @author ninestick
 */
public enum SmsCode implements Recode {
    /** 短信服务调用失败，不向客户端暴露云端内部错误。 */
    SERVICE_UNAVAILABLE("SMS_SERVICE_UNAVAILABLE", "短信服务暂时不可用，请稍后重试"),
    /** 阿里云拒绝过于频繁的发送请求。 */
    SEND_TOO_FREQUENT("SMS_SEND_TOO_FREQUENT", "发送过于频繁，请稍后重试"),
    /** 厂商明确拒绝发送，可以安全释放业务层预占额度。 */
    SEND_REJECTED("SMS_SEND_REJECTED", "短信服务暂时不可用，请稍后重试");

    /** 面向调用方的稳定错误码。 */
    private final String code;
    /** 默认中文错误消息。 */
    private final String message;

    SmsCode(String code, String message) {
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
