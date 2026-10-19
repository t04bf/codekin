package org.ninestick.codekin.captcha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.common.AnyMap;
import cloud.tianai.captcha.common.response.ApiResponse;
import cloud.tianai.captcha.spring.autoconfiguration.CacheStoreAutoConfiguration;
import cloud.tianai.captcha.spring.autoconfiguration.ImageCaptchaAutoConfiguration;
import cloud.tianai.captcha.spring.autoconfiguration.SecondaryVerificationProperties;
import cloud.tianai.captcha.spring.plugins.secondary.SecondaryVerificationApplication;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.captcha.domain.dto.CaptchaCheckDto;
import org.ninestick.codekin.redis.RedisService;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 验证 Boot 4 自动装配、真实图片生成、JSON 缓存和 Redis 一次性消费；测试键随机隔离。
 *
 * @author ninestick
 */
@EnabledIfEnvironmentVariable(named = "CODEKIN_TEST_REDIS", matches = "true")
class CaptchaRedisIntegrationTest {
    /** 随机隔离的本次测试命名空间。 */
    private String prefix;
    /** 只清理本次测试写入的键。 */
    private final Set<String> ownedKeys = new HashSet<>();
    /** 测试连接的生命周期。 */
    private LettuceConnectionFactory connectionFactory;
    /** 生产缓存适配器使用的模板。 */
    private StringRedisTemplate template;
    /** 项目公共操作入口。 */
    private RedisService redis;

    @BeforeEach
    void setUp() {
        prefix = "codekin:test:captcha:" + UUID.randomUUID();
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                System.getenv().getOrDefault("CODEKIN_TEST_REDIS_HOST", "127.0.0.1"),
                Integer.parseInt(System.getenv().getOrDefault("CODEKIN_TEST_REDIS_PORT", "6379")));
        config.setUsername(System.getenv().getOrDefault("CODEKIN_TEST_REDIS_USERNAME", "default"));
        config.setPassword(System.getenv("CODEKIN_TEST_REDIS_PASSWORD"));
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        template = new StringRedisTemplate(connectionFactory);
        redis = new RedisService(template);
    }

    @AfterEach
    void cleanUp() {
        try {
            redis.delete(ownedKeys);
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void boot4AutoConfigurationGeneratesRealImageAndCannotSkipVerification() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CaptchaAutoConfiguration.class,
                        CacheStoreAutoConfiguration.class, ImageCaptchaAutoConfiguration.class))
                .withBean(StringRedisTemplate.class, () -> template)
                .withPropertyValues("captcha.prefix=" + prefix, "captcha.init-default-resource=true",
                        "captcha.local-cache-enabled=false", "captcha.expire.default=2000",
                        "captcha.secondary.enabled=true", "captcha.secondary.key-prefix=" + prefix + ":verified")
                .run(context -> {
                    assertNotNull(context.getBean(CaptchaService.class));
                    assertTrue(context.getBean(cloud.tianai.captcha.cache.CacheStore.class)
                            instanceof CaptchaCacheStore);
                    var service = context.getBean(CaptchaService.class);
                    var image = service.generate(prefix);
                    ownedKeys.add(CaptchaConstants.GENERATE_IP_PREFIX + prefix);
                    // 图片状态位于组件配置的随机测试前缀下，不触及业务验证码键。
                    ownedKeys.add(prefix + ":" + image.getId());
                    assertNotNull(redis.get(prefix + ":" + image.getId()));
                    assertTrue(image.getBackgroundImage().startsWith("data:image/"));
                    assertTrue(image.getTemplateImage().startsWith("data:image/"));
                    assertEquals(CaptchaCode.REQUIRED,
                            assertThrows(BusinessException.class, () -> service.consume(image.getId())).getRecode());
                    ImageCaptchaTrack track = new ImageCaptchaTrack();
                    track.setBgImageWidth(image.getBackgroundImageWidth());
                    track.setBgImageHeight(image.getBackgroundImageHeight());
                    track.setTemplateImageWidth(image.getTemplateImageWidth());
                    track.setTemplateImageHeight(image.getTemplateImageHeight());
                    track.setStartTime(System.currentTimeMillis() - 500);
                    track.setStopTime(System.currentTimeMillis());
                    track.setTrackList(List.of(new ImageCaptchaTrack.Track(0F, 0F, 0F, "down"),
                            new ImageCaptchaTrack.Track(0F, 0F, 500F, "up")));
                    // 未拖动的轨迹必定偏离随机缺口，同时验证 JSON 缓存能被真正匹配流程读取。
                    assertEquals(CaptchaCode.INVALID, assertThrows(BusinessException.class,
                            () -> service.verify(CaptchaCheckDto.builder().id(image.getId()).data(track).build()))
                            .getRecode());
                    assertNull(redis.get(prefix + ":" + image.getId()));
                });
    }

    @Test
    void jsonRoundTripExpiryAndConcurrentSecondaryConsumptionUseRealRedis() throws Exception {
        var cache = new CaptchaCacheStore(redis);
        String cacheKey = prefix + ":json";
        ownedKeys.add(cacheKey);
        AnyMap payload = AnyMap.create().set("offset", 0.375F).set("createdAt", 1790960000000L);
        cache.setCache(cacheKey, payload, 2L, TimeUnit.SECONDS);
        assertEquals(0.375F, cache.getCache(cacheKey).getFloat("offset"));
        assertEquals(1790960000000L, cache.getCache(cacheKey).getLong("createdAt"));
        assertTrue(redis.getExpireSeconds(cacheKey) > 0);

        ImageCaptchaApplication delegate = mock(ImageCaptchaApplication.class);
        when(delegate.getCacheStore()).thenReturn(cache);
        ImageCaptchaTrack track = new ImageCaptchaTrack();
        String id = "SLIDER_test";
        when(delegate.matching(id, track)).thenReturn(ApiResponse.ofSuccess());
        SecondaryVerificationProperties properties = new SecondaryVerificationProperties();
        properties.setEnabled(true);
        properties.setExpire(2000L);
        properties.setKeyPrefix(prefix + ":verified");
        var application = new SecondaryVerificationApplication(delegate, properties);
        assertTrue(application.matching(id, track).isSuccess());
        ownedKeys.add(prefix + ":verified:" + id);

        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> requests = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 8; i++) {
                requests.add(executor.submit(() -> {
                    start.await();
                    return application.secondaryVerification(id);
                }));
            }
            start.countDown();
            int successes = 0;
            for (var request : requests) {
                if (request.get()) successes++;
            }
            assertEquals(1, successes);
        }
        assertEquals(false, application.secondaryVerification(id));

        // 真实 Redis 的 TTL 到期后，核验成功记录也不能再用于发送。
        application.matching(id, track);
        Thread.sleep(2100);
        assertEquals(false, application.secondaryVerification(id));
    }
}
