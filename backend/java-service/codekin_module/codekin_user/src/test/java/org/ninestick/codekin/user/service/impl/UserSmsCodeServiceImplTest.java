package org.ninestick.codekin.user.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.redis.RedisService;
import org.ninestick.codekin.security.AuthenticatedUser;
import org.ninestick.codekin.security.UserContextHolder;
import org.ninestick.codekin.sms.SmsCode;
import org.ninestick.codekin.sms.SmsSender;
import org.ninestick.codekin.user.config.UserSmsProperties;
import org.ninestick.codekin.user.domain.enums.UserSmsCode;
import org.ninestick.codekin.user.verification.VerificationCodeRateLimiter;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 验证用户业务层持有额度、验证码和登录/绑定隔离规则。
 *
 * @author ninestick
 */
class UserSmsCodeServiceImplTest {
    /** 被测验证码业务。 */
    private UserSmsCodeServiceImpl service;
    /** 厂商适配器。 */
    private SmsSender sender;
    /** 发送额度。 */
    private VerificationCodeRateLimiter rateLimiter;
    /** 验证码摘要存储。 */
    private RedisService redisService;
    /** 测试用策略。 */
    private UserSmsProperties properties;
    /** 测试占位凭证。 */
    private VerificationCodeRateLimiter.Reservation reservation;

    @BeforeEach
    void setUp() {
        service = new UserSmsCodeServiceImpl();
        sender = mock(SmsSender.class);
        rateLimiter = mock(VerificationCodeRateLimiter.class);
        redisService = mock(RedisService.class);
        properties = new UserSmsProperties();
        properties.setCodeHashSecret("0123456789abcdef0123456789abcdef");
        reservation = VerificationCodeRateLimiter.Reservation.builder().keys(List.of("cooldown")).token("token").build();
        ReflectionTestUtils.setField(service, "smsSender", sender);
        ReflectionTestUtils.setField(service, "rateLimiter", rateLimiter);
        ReflectionTestUtils.setField(service, "redisService", redisService);
        ReflectionTestUtils.setField(service, "properties", properties);
        service.validateConfiguration();
        UserContextHolder.setClientIp("203.0.113.4");
        when(rateLimiter.reserve(any())).thenReturn(reservation);
    }

    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    @Test
    void loginCodeUsesGatewayIpAndStoresDigestInLoginSpace() {
        service.sendLoginCode("13800138000");

        ArgumentCaptor<VerificationCodeRateLimiter.Request> request =
                ArgumentCaptor.forClass(VerificationCodeRateLimiter.Request.class);
        verify(rateLimiter).reserve(request.capture());
        assertEquals("sms:cooldown:LOGIN:13800138000", request.getValue().getCooldownKey());
        assertEquals("13800138000", request.getValue().getQuotas().get(0).getSubject());
        assertEquals("203.0.113.4", request.getValue().getQuotas().get(1).getSubject());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(sender).sendCode(org.mockito.ArgumentMatchers.eq("13800138000"), code.capture(),
                org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(5)));
        ArgumentCaptor<String> digest = ArgumentCaptor.forClass(String.class);
        verify(rateLimiter).complete(org.mockito.ArgumentMatchers.eq(reservation),
                org.mockito.ArgumentMatchers.eq("sms:code:LOGIN:13800138000"),
                org.mockito.ArgumentMatchers.eq("sms:attempt:LOGIN:13800138000"), digest.capture(), any(), any());
        assertTrue(code.getValue().matches("[0-9]{6}"));
        assertTrue(digest.getValue().matches("[0-9a-f]{64}"));
        assertNotEquals(code.getValue(), digest.getValue());
    }

    @Test
    void bindingCannotConsumeLoginCode() {
        UserContextHolder.set(AuthenticatedUser.builder().userId(99L).sessionId("session").build());
        when(redisService.get("sms:code:BIND:99:13800138000")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.verifyBindCode("13800138000", "123456"));

        assertEquals(UserSmsCode.CODE_EXPIRED, exception.getRecode());
        verify(redisService, never()).compareAndDelete(anyString(), anyString());
    }

    @Test
    void loginCodeIsConsumedAtomicallyAndCannotBeReplayed() {
        when(redisService.get("sms:code:LOGIN:13800138000")).thenReturn("stored-digest", (String) null);
        when(redisService.incrementWithinLimit("sms:attempt:LOGIN:13800138000", 5L,
                Duration.ofMinutes(5))).thenReturn(true);
        when(redisService.compareAndDelete(org.mockito.ArgumentMatchers.eq("sms:code:LOGIN:13800138000"),
                anyString())).thenReturn(true);

        service.verifyLoginCode("13800138000", "123456");
        BusinessException replay = assertThrows(BusinessException.class,
                () -> service.verifyLoginCode("13800138000", "123456"));

        assertEquals(UserSmsCode.CODE_EXPIRED, replay.getRecode());
        verify(redisService, times(1)).compareAndDelete(
                org.mockito.ArgumentMatchers.eq("sms:code:LOGIN:13800138000"), anyString());
    }

    @Test
    void definiteRejectionReleasesQuotaButUnknownFailureKeepsIt() {
        org.mockito.Mockito.doThrow(new BusinessException(SmsCode.SEND_REJECTED)).when(sender)
                .sendCode(anyString(), anyString(), any());
        assertThrows(BusinessException.class, () -> service.sendLoginCode("13800138000"));
        verify(rateLimiter).release(reservation, true);

        org.mockito.Mockito.clearInvocations(rateLimiter);
        org.mockito.Mockito.doThrow(new BusinessException(SmsCode.SERVICE_UNAVAILABLE)).when(sender)
                .sendCode(anyString(), anyString(), any());
        assertThrows(BusinessException.class, () -> service.sendLoginCode("13800138000"));
        verify(rateLimiter, never()).release(reservation, true);
    }
}
