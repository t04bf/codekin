package org.ninestick.codekin.user.email;

/**
 * 邮箱验证码的用途隔离与共享发送额度键。
 *
 * @author ninestick
 */
public final class UserEmailConstants {
    /** 保留已有登录验证码键，避免升级时使有效验证码失效。 */
    public static final String LOGIN_PREFIX = "codekin:email:login:";
    /** 绑定验证码额外按当前用户隔离。 */
    public static final String BIND_PREFIX = "codekin:email:bind:";
    /** 沿用原有计数键，登录和换绑共享邮箱日额度。 */
    public static final String DAILY_ADDRESS_PREFIX = LOGIN_PREFIX + "daily:address:";
    /** 沿用原有计数键，登录和换绑共享 IP 日额度。 */
    public static final String DAILY_IP_PREFIX = LOGIN_PREFIX + "daily:ip:";

    private UserEmailConstants() {
    }
}
