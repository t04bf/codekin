package org.ninestick.codekin.gateway;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关客户端 IP 获取方式，由网关专属 Nacos 配置决定。
 *
 * @author ninestick
 */
@ConfigurationProperties(prefix = "codekin.gateway.client-ip")
@Getter
@Setter
public class GatewayClientIpProperties {
    /** 直连使用 TCP 源地址；代理模式仅信任指定代理传来的 X-Forwarded-For。 */
    private Mode mode = Mode.DIRECT;

    /** 可信代理的 IP 或 CIDR；代理模式必须明确填写实际连接网关的代理地址范围。 */
    private List<String> trustedProxies = new ArrayList<>();

    /**
     * 网关部署方式。
     *
     * @author ninestick
     */
    public enum Mode {
        /** 客户端直接连接网关。 */
        DIRECT,
        /** Nginx 等可信代理连接网关并追加 X-Forwarded-For。 */
        TRUSTED_PROXY
    }
}
