package org.ninestick.codekin.captcha;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.cache.CacheStore;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.resource.ResourceStore;
import cloud.tianai.captcha.resource.common.model.dto.Resource;
import cloud.tianai.captcha.resource.impl.LocalMemoryResourceStore;
import org.ninestick.codekin.redis.RedisConfiguration;
import org.ninestick.codekin.redis.RedisService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * 自动装配公共滑块服务与 RedisService 缓存适配，HTTP 接口由业务模块定义。
 *
 * @author ninestick
 */
@AutoConfiguration(beforeName = {"cloud.tianai.captcha.spring.autoconfiguration.CacheStoreAutoConfiguration",
        "cloud.tianai.captcha.spring.autoconfiguration.ImageCaptchaAutoConfiguration"})
@EnableConfigurationProperties(CaptchaProperties.class)
@Import(RedisConfiguration.class)
public class CaptchaAutoConfiguration {
    /** 替换组件默认缓存，项目的 Redis 操作统一经过 RedisService。 */
    @Bean
    @ConditionalOnMissingBean(CacheStore.class)
    public CacheStore captchaCacheStore(RedisService redisService) {
        return new CaptchaCacheStore(redisService);
    }

    /** 图片素材驻留本地内存，使用依赖自带背景；答案与凭据仍共享 Redis。 */
    @Bean
    @ConditionalOnMissingBean(ResourceStore.class)
    public ResourceStore captchaResourceStore() {
        LocalMemoryResourceStore store = new LocalMemoryResourceStore();
        store.addResource(CaptchaTypeConstant.SLIDER,
                new Resource("classpath", "META-INF/cut-image/resource/1.jpg", null));
        return store;
    }

    /** 可供各业务服务调用的验证组件。 */
    @Bean
    @ConditionalOnMissingBean(CaptchaService.class)
    public CaptchaService captchaService(ImageCaptchaApplication application, RedisService redisService,
                                         CaptchaProperties properties) {
        return new CaptchaService(application, redisService, properties);
    }

}
