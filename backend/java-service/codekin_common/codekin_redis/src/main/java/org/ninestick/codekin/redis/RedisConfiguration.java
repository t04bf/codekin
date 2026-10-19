package org.ninestick.codekin.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 公共 RedisService 的显式装配入口，业务模块可独立导入。
 *
 * @author ninestick
 */
@Configuration(proxyBeanMethods = false)
public class RedisConfiguration {

    @Bean
    public RedisService redisService(StringRedisTemplate redisTemplate) {
        return new RedisService(redisTemplate);
    }
}
