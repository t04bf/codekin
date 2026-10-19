package org.ninestick.codekin.user.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.redis.RedisService;
import org.ninestick.codekin.sms.SmsCode;
import org.ninestick.codekin.user.domain.enums.UserEmailCode;
import org.ninestick.codekin.user.domain.enums.UserSmsCode;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 验证共用限流工具的键隔离、声明式错误码及过期占位保护。
 *
 * @author ninestick
 */
class VerificationCodeRateLimiterTest {
    /** Redis 脚本执行替身。 */
    private RedisService redis;
    /** 短信和邮箱共用的被测组件。 */
    private VerificationCodeRateLimiter limiter;

    @BeforeEach
    void setUp() {
        redis = mock(RedisService.class);
        limiter = new VerificationCodeRateLimiter();
        ReflectionTestUtils.setField(limiter, "redisService", redis);
    }

    @Test
    void smsScopesSeparateCooldownButShareDailyQuotas() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);
        limiter.reserve(smsRequest("LOGIN"));
        limiter.reserve(smsRequest("BIND"));

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis, times(2)).execute(any(RedisScript.class), keys.capture(), any(Object[].class));
        assertEquals("sms:cooldown:LOGIN:13800138000", keys.getAllValues().get(0).getFirst());
        assertEquals("sms:cooldown:BIND:13800138000", keys.getAllValues().get(1).getFirst());
        assertEquals(keys.getAllValues().get(0).get(1), keys.getAllValues().get(1).get(1));
        assertEquals(keys.getAllValues().get(0).get(2), keys.getAllValues().get(1).get(2));
        assertTrue(keys.getAllValues().get(0).get(1).endsWith(":13800138000"));
    }

    @Test
    void exceededQuotaThrowsDeclaredEmailCode() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(2L);
        var request = VerificationCodeRateLimiter.Request.builder()
                .cooldownKey("email:cooldown:address").reservationTtl(Duration.ofMinutes(5))
                .cooldownCode(UserEmailCode.SEND_COOLDOWN).failureCode(UserEmailCode.SERVICE_UNAVAILABLE)
                .quotas(List.of(
                        VerificationCodeRateLimiter.DailyQuota.builder().keyPrefix("email:daily:address:")
                                .subject("address").limit(20).exceededCode(UserEmailCode.ADDRESS_LIMIT).build(),
                        VerificationCodeRateLimiter.DailyQuota.builder().keyPrefix("email:daily:ip:")
                                .subject("ip").limit(100).exceededCode(UserEmailCode.IP_LIMIT).build()))
                .build();
        BusinessException error = assertThrows(BusinessException.class, () -> limiter.reserve(request));
        assertEquals(UserEmailCode.IP_LIMIT, error.getRecode());
    }

    @Test
    void cooldownIncludesRemainingSeconds() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(-1L);
        when(redis.getExpireSeconds("sms:cooldown:LOGIN:13800138000")).thenReturn(42L);
        BusinessException error = assertThrows(BusinessException.class, () -> limiter.reserve(smsRequest("LOGIN")));
        assertEquals(UserSmsCode.SEND_COOLDOWN, error.getRecode());
        assertEquals("请 42 秒后重试", error.getMessage());
    }

    @Test
    void lateCompletionFailsInsteadOfOverwritingNewCode() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);
        var reservation = VerificationCodeRateLimiter.Reservation.builder()
                .keys(List.of("cooldown")).token("old-request").failureCode(UserEmailCode.SERVICE_UNAVAILABLE).build();
        BusinessException error = assertThrows(BusinessException.class, () -> limiter.complete(
                reservation, "code", "attempt", "digest", Duration.ofMinutes(5), Duration.ofSeconds(60)));
        assertEquals(UserEmailCode.SERVICE_UNAVAILABLE, error.getRecode());
    }

    /** 短信的键与配额策略由调用方声明，工具不内置具体手机号规则。 */
    private VerificationCodeRateLimiter.Request smsRequest(String scope) {
        return VerificationCodeRateLimiter.Request.builder()
                .cooldownKey("sms:cooldown:" + scope + ":13800138000").reservationTtl(Duration.ofMinutes(5))
                .cooldownCode(UserSmsCode.SEND_COOLDOWN).failureCode(SmsCode.SERVICE_UNAVAILABLE)
                .quotas(List.of(
                        VerificationCodeRateLimiter.DailyQuota.builder().keyPrefix("sms:daily:phone:")
                                .subject("13800138000").limit(50).exceededCode(UserSmsCode.PHONE_DAILY_LIMIT).build(),
                        VerificationCodeRateLimiter.DailyQuota.builder().keyPrefix("sms:daily:ip:")
                                .subject("203.0.113.4").limit(100).exceededCode(UserSmsCode.IP_DAILY_LIMIT).build()))
                .build();
    }
}
