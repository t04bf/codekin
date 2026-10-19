package org.ninestick.codekin.user.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.redis.RedisService;
import org.ninestick.codekin.user.domain.enums.UserEmailCode;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 显式启用的真实 Redis 测试；只写随机隔离的测试键，结束后清理。
 *
 * @author ninestick
 */
@EnabledIfEnvironmentVariable(named = "CODEKIN_TEST_REDIS", matches = "true")
class VerificationCodeRateLimiterRedisTest {
    /** 本次测试拥有的 Redis 键，不触及业务数据。 */
    private final Set<String> ownedKeys = new HashSet<>();
    /** 隔离的测试前缀。 */
    private String prefix;
    /** 测试 Redis 连接。 */
    private LettuceConnectionFactory connectionFactory;
    /** 基础工具，使用生产代码执行真实脚本。 */
    private RedisService redis;
    /** 被测限流组件。 */
    private VerificationCodeRateLimiter limiter;

    @BeforeEach
    void setUp() {
        prefix = "codekin:test:verification:" + UUID.randomUUID() + ":";
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                System.getenv().getOrDefault("CODEKIN_TEST_REDIS_HOST", "127.0.0.1"),
                Integer.parseInt(System.getenv().getOrDefault("CODEKIN_TEST_REDIS_PORT", "6379")));
        config.setUsername(System.getenv().getOrDefault("CODEKIN_TEST_REDIS_USERNAME", "default"));
        config.setPassword(System.getenv("CODEKIN_TEST_REDIS_PASSWORD"));
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new RedisService(new StringRedisTemplate(connectionFactory));
        limiter = new VerificationCodeRateLimiter();
        ReflectionTestUtils.setField(limiter, "redisService", redis);
    }

    @AfterEach
    void cleanUp() {
        try {
            if (redis != null && !ownedKeys.isEmpty()) {
                redis.delete(ownedKeys);
            }
        } finally {
            if (connectionFactory != null) {
                connectionFactory.destroy();
            }
        }
    }

    @Test
    void quotasAndCompletionAreAtomicAndStaleRequestsCannotOverwrite() {
        var first = limiter.reserve(request("first", 1));
        ownedKeys.addAll(first.getKeys());
        BusinessException limited = assertThrows(BusinessException.class, () -> limiter.reserve(request("second", 1)));
        assertEquals(UserEmailCode.ADDRESS_LIMIT, limited.getRecode());
        assertNull(redis.get(prefix + "cooldown:second"));
        assertEquals("1", redis.get(first.getKeys().get(1)));
        assertEquals("1", redis.get(first.getKeys().get(2)));

        String codeKey = prefix + "code";
        String attemptKey = prefix + "attempt";
        ownedKeys.add(codeKey);
        ownedKeys.add(attemptKey);
        redis.set(attemptKey, "3", Duration.ofMinutes(5));
        limiter.complete(first, codeKey, attemptKey, "new-digest", Duration.ofMinutes(5), Duration.ofSeconds(60));
        assertEquals("new-digest", redis.get(codeKey));
        assertNull(redis.get(attemptKey));
        assertTrue(redis.getExpireSeconds(first.getKeys().getFirst()) <= 60);
        assertThrows(BusinessException.class, () -> limiter.complete(
                first, codeKey, attemptKey, "stale-digest", Duration.ofMinutes(5), Duration.ofSeconds(60)));
        limiter.release(first, true);
        assertEquals("new-digest", redis.get(codeKey));
        assertEquals("1", redis.get(first.getKeys().get(1)));
    }

    @Test
    void onlyOwnerCanRefundAndUncertainDeliveryKeepsQuota() {
        var first = limiter.reserve(request("first", 5));
        ownedKeys.addAll(first.getKeys());
        var wrongOwner = VerificationCodeRateLimiter.Reservation.builder()
                .keys(first.getKeys()).token("not-the-owner").build();
        limiter.release(wrongOwner, true);
        assertEquals("1", redis.get(first.getKeys().get(1)));
        limiter.release(first, true);
        assertNull(redis.get(first.getKeys().getFirst()));
        assertEquals("0", redis.get(first.getKeys().get(1)));
        assertTrue(redis.getExpireSeconds(first.getKeys().get(1)) > 0);

        var second = limiter.reserve(request("first", 5));
        limiter.release(second, false);
        assertNull(redis.get(second.getKeys().getFirst()));
        assertEquals("1", redis.get(second.getKeys().get(1)));
    }

    @Test
    void concurrentRequestsOnlyReserveOnce() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<VerificationCodeRateLimiter.Reservation>> futures = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        return limiter.reserve(request("concurrent", 50));
                    } catch (BusinessException exception) {
                        assertEquals(UserEmailCode.SEND_COOLDOWN, exception.getRecode());
                        return null;
                    }
                }));
            }
            start.countDown();
            int success = 0;
            for (var future : futures) {
                var reservation = future.get();
                if (reservation != null) {
                    success++;
                    ownedKeys.addAll(reservation.getKeys());
                    assertEquals("1", redis.get(reservation.getKeys().get(1)));
                    assertEquals("1", redis.get(reservation.getKeys().get(2)));
                }
            }
            assertEquals(1, success);
            assertFalse(ownedKeys.isEmpty());
        }
    }

    /** 不同发送场景共享同一组日额度。 */
    private VerificationCodeRateLimiter.Request request(String scope, long addressLimit) {
        return VerificationCodeRateLimiter.Request.builder()
                .cooldownKey(prefix + "cooldown:" + scope).reservationTtl(Duration.ofMinutes(5))
                .cooldownCode(UserEmailCode.SEND_COOLDOWN).failureCode(UserEmailCode.SERVICE_UNAVAILABLE)
                .quotas(List.of(
                        VerificationCodeRateLimiter.DailyQuota.builder().keyPrefix(prefix + "address:")
                                .subject("recipient").limit(addressLimit).exceededCode(UserEmailCode.ADDRESS_LIMIT).build(),
                        VerificationCodeRateLimiter.DailyQuota.builder().keyPrefix(prefix + "ip:")
                                .subject("ip").limit(100).exceededCode(UserEmailCode.IP_LIMIT).build()))
                .build();
    }
}
