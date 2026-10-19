package org.ninestick.codekin.webmvc;

import org.ninestick.codekin.redis.RedisService;
import org.ninestick.codekin.security.AuthEndpointPolicy;
import org.ninestick.codekin.security.GatewayContextConfiguration;
import org.ninestick.codekin.security.GatewayIdentityService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * 所有引用公共 WebMVC 模块的业务服务自动启用网关身份拦截器。
 *
 * @author ninestick
 */
@AutoConfiguration
@Import(GatewayContextConfiguration.class)
public class GatewayIdentityWebMvcAutoConfiguration {
    /** 创建负责验签及填充用户上下文的拦截器。 */
    @Bean
    public GatewayIdentityInterceptor gatewayIdentityInterceptor(AuthEndpointPolicy policy,
            GatewayIdentityService identityService, RedisService redisService) {
        return new GatewayIdentityInterceptor(policy, identityService, redisService);
    }

    /** 将同一拦截器注册到每个业务 API。 */
    @Bean
    public GatewayIdentityWebMvcConfigurer gatewayIdentityWebMvcConfigurer(GatewayIdentityInterceptor interceptor) {
        return new GatewayIdentityWebMvcConfigurer(interceptor);
    }
}
