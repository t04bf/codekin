package org.ninestick.codekin.user.sms;

/**
 * 用户业务的短信键和验证码格式。
 *
 * @author ninestick
 */
public final class UserSmsConstants {
    /** 登录码与绑定码使用独立的 Redis 空间。 */
    public static final String LOGIN_SCOPE = "LOGIN";
    /** 绑定码还会关联当前用户 ID。 */
    public static final String BIND_SCOPE = "BIND";
    /** 业务生成六位数字验证码。 */
    public static final int CODE_LENGTH = 6;
    /** Redis 中仅保存验证码摘要。 */
    public static final String CODE_PREFIX = "sms:code:";
    /** 同一个验证码的核验次数。 */
    public static final String ATTEMPT_PREFIX = "sms:attempt:";
    /** 同场景手机号的发送中占位与冷却。 */
    public static final String COOLDOWN_PREFIX = "sms:cooldown:";
    /** 手机号跨场景的日额度。 */
    public static final String DAILY_PHONE_PREFIX = "sms:daily:phone:";
    /** IP 跨场景的日额度。 */
    public static final String DAILY_IP_PREFIX = "sms:daily:ip:";
    /** 摘要算法。 */
    public static final String CODE_HASH_ALGORITHM = "HmacSHA256";

    private UserSmsConstants() {
    }
}
