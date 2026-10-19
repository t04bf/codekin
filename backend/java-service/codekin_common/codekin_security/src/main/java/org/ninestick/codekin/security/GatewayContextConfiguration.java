package org.ninestick.codekin.security;

import org.ninestick.codekin.redis.RedisConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 网关身份签名和路径规则的共享装配，不要求业务服务解析原始 JWT。
 *
 * @author ninestick
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayContextProperties.class)
@Import(RedisConfiguration.class)
public class GatewayContextConfiguration {
    /** 网关和业务服务使用同一密钥签发、校验内部身份头。 */
    @Bean
    public GatewayIdentityService gatewayIdentityService(GatewayContextProperties properties) {
        return new GatewayIdentityService(properties);
    }

    /** 未列入公开规则的路径默认需要认证。 */
    @Bean
    public AuthEndpointPolicy authEndpointPolicy(GatewayContextProperties properties) {
        return new AuthEndpointPolicy(properties);
    }
}
