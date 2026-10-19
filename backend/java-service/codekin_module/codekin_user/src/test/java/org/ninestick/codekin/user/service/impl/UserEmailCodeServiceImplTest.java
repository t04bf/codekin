package org.ninestick.codekin.user.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.ninestick.codekin.email.EmailSender;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.redis.RedisService;
import org.ninestick.codekin.security.AuthenticatedUser;
import org.ninestick.codekin.security.UserContextHolder;
import org.ninestick.codekin.user.config.UserEmailProperties;
import org.ninestick.codekin.user.domain.enums.UserEmailCode;
import org.ninestick.codekin.user.verification.VerificationCodeRateLimiter;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 邮箱验证码用途、用户隔离、一次性消费和共享额度的回归测试。
 * 邮件发送及 Redis 均替换为测试依赖，不会真实投递。
 *
 * @author ninestick
 */
class UserEmailCodeServiceImplTest {
    /** 被测服务。 */
    private UserEmailCodeServiceImpl service;
    /** 捕获邮件投递的六位验证码。 */
    private EmailSender sender;
    /** 共用限流工具。 */
    private VerificationCodeRateLimiter limiter;
    /** 验证码存储与原子消费入口。 */
    private RedisService redis;
    /** 模拟已登记的摘要，验证生成与消费使用相同上下文。 */
    private Map<String, String> stored;
    /** 捕获最后一次发送的验证码，仅供测试核验。 */
    private String sentCode;

    @BeforeEach
    void setUp() {
        service = new UserEmailCodeServiceImpl();
        sender = mock(EmailSender.class);
        limiter = mock(VerificationCodeRateLimiter.class);
        redis = mock(RedisService.class);
        stored = new HashMap<>();
        UserEmailProperties properties = new UserEmailProperties();
        properties.setCodeHashSecret("0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(service, "emailSender", sender);
        ReflectionTestUtils.setField(service, "rateLimiter", limiter);
        ReflectionTestUtils.setField(service, "redisService", redis);
        ReflectionTestUtils.setField(service, "properties", properties);
        UserContextHolder.set(AuthenticatedUser.builder().userId(42L).sessionId("session").build());
        UserContextHolder.setClientIp("203.0.113.4");
        when(limiter.reserve(any())).thenReturn(VerificationCodeRateLimiter.Reservation.builder()
                .keys(List.of("cooldown")).token("reservation").build());
        doAnswer(invocation -> {
            sentCode = invocation.getArgument(1);
            return null;
        }).when(sender).sendCode(anyString(), anyString(), any());
        doAnswer(invocation -> {
            stored.put(invocation.getArgument(1), invocation.getArgument(3));
            return null;
        }).when(limiter).complete(any(), anyString(), anyString(), anyString(), any(), any());
        when(redis.get(anyString())).thenAnswer(invocation -> stored.get(invocation.<String>getArgument(0)));
        when(redis.incrementWithinLimit(anyString(), anyLong(), any())).thenReturn(true);
        when(redis.compareAndDelete(anyString(), anyString())).thenAnswer(invocation ->
                stored.remove(invocation.<String>getArgument(0), invocation.<String>getArgument(1)));
    }

    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    @Test
    void loginCodeCannotBeUsedForBindingButStillLogsIn() {
        service.sendLoginCode("New@Example.Com");
        assertEquals(UserEmailCode.CODE_EXPIRED, assertThrows(BusinessException.class,
                () -> service.verifyBindCode("new@example.com", sentCode)).getRecode());
        service.verifyLoginCode("new@example.com", sentCode);
        assertTrue(stored.isEmpty());
    }

    @Test
    void bindingCodeIsUserScopedCannotLoginAndCannotBeReplayed() {
        service.sendBindCode("New@Example.Com");
        assertTrue(sentCode.matches("[0-9]{6}"));
        assertFalse(stored.containsValue(sentCode));
        assertTrue(stored.keySet().stream().noneMatch(key -> key.contains("example.com")));
        assertEquals(UserEmailCode.CODE_EXPIRED, assertThrows(BusinessException.class,
                () -> service.verifyLoginCode("new@example.com", sentCode)).getRecode());
        UserContextHolder.set(AuthenticatedUser.builder().userId(99L).sessionId("other").build());
        assertEquals(UserEmailCode.CODE_EXPIRED, assertThrows(BusinessException.class,
                () -> service.verifyBindCode("new@example.com", sentCode)).getRecode());
        UserContextHolder.set(AuthenticatedUser.builder().userId(42L).sessionId("session").build());
        service.verifyBindCode("new@example.com", sentCode);
        assertEquals(UserEmailCode.CODE_EXPIRED, assertThrows(BusinessException.class,
                () -> service.verifyBindCode("new@example.com", sentCode)).getRecode());
    }

    @Test
    void wrongCodeIsRejectedAndAttemptLimitIsEnforced() {
        service.sendBindCode("new@example.com");
        String wrongCode = "000000".equals(sentCode) ? "111111" : "000000";
        assertEquals(UserEmailCode.CODE_INVALID, assertThrows(BusinessException.class,
                () -> service.verifyBindCode("new@example.com", wrongCode)).getRecode());
        assertFalse(stored.isEmpty());
        when(redis.incrementWithinLimit(anyString(), anyLong(), any())).thenReturn(false);
        assertEquals(UserEmailCode.VERIFY_TOO_FREQUENT, assertThrows(BusinessException.class,
                () -> service.verifyBindCode("new@example.com", sentCode)).getRecode());
        verify(redis, times(1)).compareAndDelete(anyString(), anyString());
    }

    @Test
    void loginAndBindingShareDailyAddressAndIpQuotas() {
        service.sendLoginCode("New@Example.Com");
        service.sendBindCode("new@example.com");
        ArgumentCaptor<VerificationCodeRateLimiter.Request> requests =
                ArgumentCaptor.forClass(VerificationCodeRateLimiter.Request.class);
        verify(limiter, times(2)).reserve(requests.capture());
        var login = requests.getAllValues().get(0).getQuotas();
        var bind = requests.getAllValues().get(1).getQuotas();
        for (int index = 0; index < login.size(); index++) {
            assertEquals(login.get(index).getKeyPrefix(), bind.get(index).getKeyPrefix());
            assertEquals(login.get(index).getSubject(), bind.get(index).getSubject());
            assertEquals(login.get(index).getLimit(), bind.get(index).getLimit());
        }
    }
}
