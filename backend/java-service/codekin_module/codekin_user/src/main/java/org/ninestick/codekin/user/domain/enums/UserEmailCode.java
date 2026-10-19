package org.ninestick.codekin.user.domain.enums;

import lombok.Getter;
import org.ninestick.codekin.model.common.Recode;

/**
 * 邮箱验证码与发送错误码。
 *
 * @author ninestick
 */
@Getter
public enum UserEmailCode implements Recode {
    /** 邮箱格式无效。 */
    INVALID_EMAIL("EMAIL_INVALID", "请输入有效邮箱"),
    /** 相同邮箱仍处于发送冷却期。 */
    SEND_COOLDOWN("EMAIL_SEND_COOLDOWN", "请稍后重试"),
    /** 相同邮箱已达发送限额。 */
    ADDRESS_LIMIT("EMAIL_ADDRESS_LIMIT", "邮箱发送次数已达上限"),
    /** 当前网络已达发送限额。 */
    IP_LIMIT("EMAIL_IP_LIMIT", "当前网络请求过于频繁"),
    /** SMTP 投递未成功。 */
    SERVICE_UNAVAILABLE("EMAIL_SERVICE_UNAVAILABLE", "邮件服务暂时不可用，请稍后重试"),
    /** 验证码不存在或已过期。 */
    CODE_EXPIRED("EMAIL_CODE_EXPIRED", "请先获取验证码或重新发送"),
    /** 验证码不匹配。 */
    CODE_INVALID("EMAIL_CODE_INVALID", "验证码错误或已失效"),
    /** 同一验证码已超出尝试上限。 */
    VERIFY_TOO_FREQUENT("EMAIL_VERIFY_TOO_FREQUENT", "验证码尝试过于频繁，请重新获取");

    /** 业务错误码。 */
    private final String code;
    /** 客户端可见的默认消息。 */
    private final String message;

    UserEmailCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
