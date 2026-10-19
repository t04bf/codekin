package org.ninestick.codekin.security;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 签名参数；密钥由部署环境提供，不写入代码库。
 *
 * @author ninestick
 */
@ConfigurationProperties(prefix = "codekin.security.jwt")
@Getter
@Setter
public class JwtProperties {

    /** HS256 密钥的 Base64 编码，解码后至少 32 字节。 */
    private String secretBase64;

    /** 令牌发行方，用于校验令牌来源。 */
    private String issuer = "codekin";

    /** Access Token 的固定有效期。 */
    private Duration accessTtl = Duration.ofMinutes(30);

    /** Refresh Token 的空闲有效期，每次成功轮换后重新计算。 */
    private Duration refreshIdleTtl = Duration.ofDays(7);

    /** 登录会话不可延长的最长生命周期。 */
    private Duration sessionMaxLifetime = Duration.ofDays(30);

}
