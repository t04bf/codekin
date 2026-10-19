package org.ninestick.codekin.user.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 邮箱登录与绑定验证码共用的业务有效期及发送限额。
 *
 * @author ninestick
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "codekin.user.email")
public class UserEmailProperties {
    /** 所有用户服务实例共用的验证码 HMAC 密钥。 */
    private String codeHashSecret;
    /** 验证码有效时间。 */
    private Duration validTime = Duration.ofMinutes(5);
    /** 同一邮箱发送冷却时间。 */
    private Duration sendInterval = Duration.ofSeconds(60);
    /** 同一邮箱登录与绑定合计的每日发送上限。 */
    private long dailyAddressLimit = 20;
    /** 同一客户端 IP 登录与绑定合计的每日发送上限。 */
    private long dailyIpLimit = 100;
    /** 一个验证码允许的核验次数。 */
    private long verifyAttemptLimit = 5;
}
