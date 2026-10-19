package org.ninestick.codekin.gateway;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 从 Nacos 绑定网关 IP 策略，并在启动时检查可信代理配置。
 *
 * @author ninestick
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayClientIpProperties.class)
public class GatewayClientIpConfiguration {
    /** 统一由网关解析 IP，再交给原有的身份头签名流程。 */
    @Bean
    public GatewayClientIpResolver gatewayClientIpResolver(GatewayClientIpProperties properties) {
        return new GatewayClientIpResolver(properties);
    }
}
