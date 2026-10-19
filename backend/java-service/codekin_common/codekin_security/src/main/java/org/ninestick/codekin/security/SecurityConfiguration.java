package org.ninestick.codekin.security;

import org.ninestick.codekin.redis.RedisService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 安全组件的显式装配入口，供各服务按需导入。
 *
 * @author ninestick
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
@Import(GatewayContextConfiguration.class)
public class SecurityConfiguration {

    /** 密码散列采用带算法标识的编码格式，便于后续升级参数。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public JwtSessionService jwtSessionService(RedisService redisService, JwtProperties properties) {
        return new JwtSessionService(redisService, properties);
    }

}
