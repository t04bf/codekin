package org.ninestick.codekin.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关与业务服务共享的身份头签名参数和公开接口规则。
 *
 * @author ninestick
 */
@ConfigurationProperties(prefix = "codekin.security.gateway-context")
@Getter
@Setter
public class GatewayContextProperties {
    /** 独立于 JWT 的 HMAC 密钥，Base64 解码后至少 32 字节。 */
    private String secretBase64;

    /** 身份头最大有效期，默认 30 秒。 */
    private Duration maxAge = Duration.ofSeconds(30);

    /** 无需登录的接口，格式为“HTTP方法 路径模板”。 */
    private List<String> publicEndpoints = new ArrayList<>();

}
