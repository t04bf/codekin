package org.ninestick.codekin.user.domain.enums;

import lombok.Getter;
import org.ninestick.codekin.model.common.Recode;

/**
 * 用户业务的验证码与发送限额错误码。
 *
 * @author ninestick
 */
@Getter
public enum UserSmsCode implements Recode {
    /** 手机号格式不符合当前大陆号码规则。 */
    INVALID_PHONE("SMS_INVALID_PHONE", "请输入有效的中国大陆手机号"),
    /** 手机号仍在发送中或冷却期。 */
    SEND_COOLDOWN("SMS_SEND_COOLDOWN", "请稍后重试"),
    /** 手机号当日发送上限。 */
    PHONE_DAILY_LIMIT("SMS_PHONE_DAILY_LIMIT", "今日发送次数已达上限"),
    /** 客户端 IP 当日发送上限。 */
    IP_DAILY_LIMIT("SMS_IP_DAILY_LIMIT", "当前网络请求过于频繁"),
    /** 未获取验证码或验证码到期。 */
    CODE_EXPIRED("SMS_CODE_EXPIRED", "请先获取验证码或重新发送"),
    /** 验证码错误。 */
    CODE_INVALID("SMS_CODE_INVALID", "验证码错误或已失效"),
    /** 核验次数已用完。 */
    VERIFY_TOO_FREQUENT("SMS_VERIFY_TOO_FREQUENT", "验证码尝试过于频繁，请重新获取");

    /** 返回给客户端的业务错误码。 */
    private final String code;
    /** 默认错误消息。 */
    private final String message;

    UserSmsCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
