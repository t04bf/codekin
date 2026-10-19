package org.ninestick.codekin.user.service.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.ninestick.codekin.email.EmailSender;
import org.ninestick.codekin.redis.RedisService;
import org.ninestick.codekin.user.config.UserEmailProperties;
import org.ninestick.codekin.user.verification.VerificationCodeRateLimiter;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 覆盖普通 RedisTemplate 存在时，邮件业务仅依赖 RedisService 的注入。
 *
 * @author ninestick
 */
class UserEmailCodeServiceWiringTest {

    @Test
    void emailServiceUsesRedisServiceWithoutDirectTemplateInjection() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("redisTemplate", RedisTemplate.class, () -> mock(RedisTemplate.class));
            context.registerBean(EmailSender.class, () -> mock(EmailSender.class));
            context.registerBean(RedisService.class, () -> mock(RedisService.class));
            context.registerBean(UserEmailProperties.class);
            context.registerBean(UserEmailCodeServiceImpl.class);
            context.registerBean(VerificationCodeRateLimiter.class);
            context.refresh();
            assertNotNull(context.getBean(UserEmailCodeServiceImpl.class));
        }
    }
}
