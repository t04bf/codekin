package org.ninestick.codekin.user.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.time.Instant;
import java.time.LocalDateTime;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.security.AuthenticatedUser;
import org.ninestick.codekin.security.JwtSessionService;
import org.ninestick.codekin.security.UserContextHolder;
import org.ninestick.codekin.user.domain.dto.info.EmailVerifyDto;
import org.ninestick.codekin.user.domain.dto.info.PasswordSetDto;
import org.ninestick.codekin.user.domain.entity.info.UserAuthEntity;
import org.ninestick.codekin.user.domain.entity.info.UserProfileEntity;
import org.ninestick.codekin.user.domain.enums.UserAccountStatus;
import org.ninestick.codekin.user.domain.enums.UserCode;
import org.ninestick.codekin.user.mapper.UserAuthMapper;
import org.ninestick.codekin.user.mapper.UserProfileMapper;
import org.ninestick.codekin.user.service.IUserEmailCodeService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.ninestick.codekin.user.service.IUserAvatarService;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 邮箱首次建号、旧账号保护和密码修改撤销会话的业务测试。
 *
 * @author ninestick
 */
class UserServiceImplEmailPasswordTest {
    /** 被测用户业务。 */
    private UserServiceImpl service;
    /** 隔离默认头像分配。 */
    private IUserAvatarService avatars;
    /** 认证表持久化。 */
    private UserAuthMapper authMapper;
    /** 资料表持久化。 */
    private UserProfileMapper profileMapper;
    /** 邮箱验证码核验。 */
    private IUserEmailCodeService emailCodeService;
    /** Redis 会话操作。 */
    private JwtSessionService sessions;
    /** 密码散列。 */
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "unit-test"),
                UserAuthEntity.class);
        service = new UserServiceImpl();
        avatars = mock(IUserAvatarService.class);
        ReflectionTestUtils.setField(service, "userAvatarService", avatars);
        authMapper = mock(UserAuthMapper.class);
        profileMapper = mock(UserProfileMapper.class);
        emailCodeService = mock(IUserEmailCodeService.class);
        sessions = mock(JwtSessionService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        ReflectionTestUtils.setField(service, "authMapper", authMapper);
        ReflectionTestUtils.setField(service, "profileMapper", profileMapper);
        ReflectionTestUtils.setField(service, "userEmailCodeService", emailCodeService);
        ReflectionTestUtils.setField(service, "jwtSessionService", sessions);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(service, "transactionTemplate", transactions);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @Test
    void firstEmailLoginCreatesVerifiedAuthAndProfile() {
        when(avatars.pickRandomDefaultAvatarKey()).thenReturn("uploads/avatar/default/avatar-01-wink.png");
        when(authMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(authMapper.insert(any(UserAuthEntity.class))).thenAnswer(invocation -> {
            UserAuthEntity auth = invocation.getArgument(0);
            assertEquals("member@example.com", auth.getEmail());
            auth.setId(42L);
            return 1;
        });
        when(profileMapper.insert(any(UserProfileEntity.class))).thenAnswer(invocation -> {
            UserProfileEntity profile = invocation.getArgument(0);
            assertEquals(42L, profile.getUserId());
        assertEquals("uploads/avatar/default/avatar-01-wink.png", profile.getAvatarKey());
            return 1;
        });
        when(sessions.issue(42L)).thenReturn(JwtSessionService.IssuedToken.builder()
                .userId(42L).token("access").refreshToken("refresh")
                .expiresAt(Instant.now()).refreshExpiresAt(Instant.now())
                .sessionExpiresAt(Instant.now()).build());

        var result = service.emailLogin(EmailVerifyDto.builder()
                .email("Member@Example.Com").code("123456").build(), "127.0.0.1");

        verify(emailCodeService).verifyLoginCode("member@example.com", "123456");
        assertEquals("42", result.getUserId());
        assertEquals("refresh", result.getRefreshToken());
    }

    @Test
    void unverifiedEmailCannotClaimExistingAccount() {
        when(authMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(UserAuthEntity.builder()
                .id(42L).status(UserAccountStatus.NORMAL.getCode())
                .email("member@example.com").build());
        BusinessException error = assertThrows(BusinessException.class, () -> service.emailLogin(
                EmailVerifyDto.builder().email("member@example.com").code("123456").build(), "127.0.0.1"));
        assertEquals(UserCode.EMAIL_UNVERIFIED, error.getRecode());
        verify(sessions, never()).issue(any());
    }

    /** 验证码建号后允许首次设置密码，且撤销原验证码登录会话。 */
    @Test
    void firstPasswordDoesNotRequireOldPasswordAndRevokesSessions() {
        UserContextHolder.set(AuthenticatedUser.builder().userId(42L).sessionId("current").build());
        try {
            when(authMapper.selectById(42L)).thenReturn(UserAuthEntity.builder().id(42L)
                    .status(UserAccountStatus.NORMAL.getCode()).build());
            when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
            when(authMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
            service.setPassword(PasswordSetDto.builder().newPassword("new-password").build());
            verify(passwordEncoder, never()).matches(any(), any());
            verify(sessions, times(2)).revokeAll(42L);
        } finally {
            UserContextHolder.clear();
        }
    }

    @Test
    void changingPasswordRequiresOldPasswordAndRevokesSessions() {
        UserContextHolder.set(AuthenticatedUser.builder().userId(42L).sessionId("current").build());
        try {
            when(authMapper.selectById(42L)).thenReturn(UserAuthEntity.builder().id(42L)
                    .status(UserAccountStatus.NORMAL.getCode()).passwordHash("old-hash")
                    .createdAt(LocalDateTime.now()).build());
            when(passwordEncoder.matches("old", "old-hash")).thenReturn(true);
            when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
            when(authMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

            service.setPassword(PasswordSetDto.builder().oldPassword("old")
                    .newPassword("new-password").build());
            verify(sessions, times(2)).revokeAll(42L);
            verify(authMapper).update(isNull(), any(LambdaUpdateWrapper.class));
        } finally {
            UserContextHolder.clear();
        }
    }

    @Test
    void wrongOldPasswordDoesNotModifyAccount() {
        UserContextHolder.set(AuthenticatedUser.builder().userId(42L).sessionId("current").build());
        try {
            when(authMapper.selectById(42L)).thenReturn(UserAuthEntity.builder().id(42L)
                    .status(UserAccountStatus.NORMAL.getCode()).passwordHash("old-hash").build());
            BusinessException error = assertThrows(BusinessException.class, () -> service.setPassword(
                    PasswordSetDto.builder().oldPassword("wrong").newPassword("new-password").build()));
            assertEquals(UserCode.PASSWORD_OLD_INVALID, error.getRecode());
            verify(sessions, never()).revokeAll(42L);
        } finally {
            UserContextHolder.clear();
        }
    }
}
