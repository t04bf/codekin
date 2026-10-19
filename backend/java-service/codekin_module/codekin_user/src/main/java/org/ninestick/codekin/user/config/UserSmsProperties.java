package org.ninestick.codekin.user.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * 用户业务的短信限流、验证码有效期与摘要配置。
 *
 * @author ninestick
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "codekin.user.sms")
public class UserSmsProperties {
    /** 所有用户服务实例共用的验证码 HMAC 密钥。 */
    private String codeHashSecret;
    /** 一个手机号跨业务的每日发送上限。 */
    private long dailyPhoneLimit = 50;
    /** 一个客户端 IP 跨业务的每日发送上限。 */
    private long dailyIpLimit = 100;
    /** 登录验证码策略。 */
    private Policy login = new Policy();
    /** 绑定验证码策略。 */
    private Policy bind = new Policy();

    /** 启动时拒绝缺失密钥和无效限额。 */
    public void validate() {
        Assert.isTrue(StringUtils.hasText(codeHashSecret)
                && codeHashSecret.getBytes(StandardCharsets.UTF_8).length >= 32,
                "必须配置至少 32 字节的短信验证码摘要密钥");
        Assert.isTrue(dailyPhoneLimit > 0 && dailyIpLimit > 0, "短信自然日发送上限必须为正数");
        Assert.notNull(login, "登录短信策略不能为空");
        Assert.notNull(bind, "绑定短信策略不能为空");
        for (Policy policy : new Policy[]{login, bind}) {
            Assert.isTrue(policy.validTime != null && policy.validTime.toMinutes() > 0
                    && policy.sendInterval != null && policy.sendInterval.toSeconds() > 0
                    && policy.verifyAttemptLimit > 0,
                    "短信策略必须使用正数和正时长");
        }
    }

    /**
     * 单个用户业务的验证码策略。
     *
     * @author ninestick
     */
    @Getter
    @Setter
    public static class Policy {
        /** 验证码有效期。 */
        private Duration validTime = Duration.ofMinutes(5);
        /** 发送冷却时间。 */
        private Duration sendInterval = Duration.ofSeconds(60);
        /** 同一验证码的核验次数上限。 */
        private long verifyAttemptLimit = 5;
    }
}
