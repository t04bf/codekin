package org.ninestick.codekin.sms;

import java.util.Set;

/**
 * 阿里云号码认证协议值；这些不是运行时策略。
 *
 * @author ninestick
 */
public final class SmsConstants {
    /** 阿里云号码认证公共接入点。 */
    public static final String DYPNSAPI_ENDPOINT = "dypnsapi.aliyuncs.com";
    /** 阿里云接口调用成功码。 */
    public static final String API_OK = "OK";
    /** 阿里云发送频率限制码。 */
    public static final String FREQUENCY_FAIL = "FREQUENCY_FAIL";
    /** 阿里云号码日限额码。 */
    public static final String BUSINESS_LIMIT_CONTROL = "BUSINESS_LIMIT_CONTROL";
    /** 阿里云拒绝 AccessKey 请求签名，短信请求未被受理。 */
    public static final String SIGNATURE_DOES_NOT_MATCH = "SignatureDoesNotMatch";
    /** 阿里云明确拒绝请求的已知错误码；网络超时或未知异常不退还发送额度。 */
    public static final Set<String> DEFINITE_REJECTION_CODES = Set.of(
            FREQUENCY_FAIL, BUSINESS_LIMIT_CONTROL, "MOBILE_NUMBER_ILLEGAL",
            "INVALID_PARAMETERS", "FUNCTION_NOT_OPENED");
    /** 中国大陆手机号持久化前缀。 */
    public static final String CHINA_DIAL_CODE = "+86";
    private SmsConstants() {
    }
}
