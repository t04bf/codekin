package org.ninestick.codekin.user.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.time.Instant;
import java.time.LocalDateTime;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.security.AuthenticatedUser;
import org.ninestick.codekin.security.JwtSessionService;
import org.ninestick.codekin.security.UserContextHolder;
import org.ninestick.codekin.user.domain.dto.info.SmsVerifyDto;
import org.ninestick.codekin.user.domain.entity.info.UserAuthEntity;
import org.ninestick.codekin.user.domain.entity.info.UserProfileEntity;
import org.ninestick.codekin.user.domain.enums.UserAccountStatus;
import org.ninestick.codekin.user.domain.enums.UserCode;
import org.ninestick.codekin.user.domain.enums.UserSmsCode;
import org.ninestick.codekin.user.mapper.UserAuthMapper;
import org.ninestick.codekin.user.mapper.UserProfileMapper;
import org.ninestick.codekin.user.service.IUserSmsCodeService;
import org.springframework.test.util.ReflectionTestUtils;
import org.ninestick.codekin.user.service.IUserAvatarService;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 覆盖短信登录建号及未验证旧账号的接管风险。
 *
 * @author ninestick
 */
class UserServiceImplSmsTest {
    /** 被测用户服务。 */
    private UserServiceImpl service;
    /** 隔离默认头像分配。 */
    private IUserAvatarService avatars;
    /** 认证表 Mapper。 */
    private UserAuthMapper authMapper;
    /** 基础资料表 Mapper。 */
    private UserProfileMapper profileMapper;
    /** 短信核验基础设施。 */
    private IUserSmsCodeService userSmsCodeService;
    /** 登录会话签发。 */
    private JwtSessionService jwtSessionService;
    /** 注册事务。 */
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        // 单元测试未启动 MyBatis，需显式注册 Lambda 字段元数据。
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "unit-test"),
                UserAuthEntity.class);
        service = new UserServiceImpl();
        avatars = mock(IUserAvatarService.class);
        ReflectionTestUtils.setField(service, "userAvatarService", avatars);
        authMapper = mock(UserAuthMapper.class);
        profileMapper = mock(UserProfileMapper.class);
        userSmsCodeService = mock(IUserSmsCodeService.class);
        jwtSessionService = mock(JwtSessionService.class);
        transactionTemplate = mock(TransactionTemplate.class);
        ReflectionTestUtils.setField(service, "authMapper", authMapper);
        ReflectionTestUtils.setField(service, "profileMapper", profileMapper);
        ReflectionTestUtils.setField(service, "userSmsCodeService", userSmsCodeService);
        ReflectionTestUtils.setField(service, "jwtSessionService", jwtSessionService);
        ReflectionTestUtils.setField(service, "transactionTemplate", transactionTemplate);
    }

    @Test
    void firstSmsLoginCreatesAuthAndProfileWithSameId() {
        when(avatars.pickRandomDefaultAvatarKey()).thenReturn("uploads/avatar/default/avatar-01-wink.png");
        SmsVerifyDto request = SmsVerifyDto.builder().phone("13800138000").code("123456").build();
        when(authMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            UserAuthEntity entity = invocation.getArgument(0);
            entity.setId(12345L);
            return 1;
        }).when(authMapper).insert(any(UserAuthEntity.class));
        when(profileMapper.insert(any(UserProfileEntity.class))).thenReturn(1);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        when(jwtSessionService.issue(12345L)).thenReturn(JwtSessionService.IssuedToken.builder()
                .userId(12345L).token("jwt").refreshToken("refresh").expiresAt(Instant.now())
                .refreshExpiresAt(Instant.now().plusSeconds(60))
                .sessionExpiresAt(Instant.now().plusSeconds(120)).build());

        assertEquals("12345", service.smsLogin(request, "127.0.0.1").getUserId());
        ArgumentCaptor<UserAuthEntity> auth = ArgumentCaptor.forClass(UserAuthEntity.class);
        ArgumentCaptor<UserProfileEntity> profile = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(authMapper).insert(auth.capture());
        verify(profileMapper).insert(profile.capture());
        assertEquals("+8613800138000", auth.getValue().getPhone());
        assertEquals(UserAccountStatus.NORMAL.getCode(), auth.getValue().getStatus());
        assertEquals(12345L, profile.getValue().getUserId());
        assertEquals("用户12345", profile.getValue().getNickname());
        assertEquals("uploads/avatar/default/avatar-01-wink.png", profile.getValue().getAvatarKey());
    }

    @Test
    void unverifiedPhoneCannotLogIntoExistingAccount() {
        SmsVerifyDto request = SmsVerifyDto.builder().phone("13800138000").code("123456").build();
        UserAuthEntity oldAccount = UserAuthEntity.builder()
                .id(99L)
                .status(UserAccountStatus.NORMAL.getCode())
                .phone("+8613800138000")
                .phoneVerifiedAt(null)
                .createdAt(LocalDateTime.now())
                .build();
        when(authMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(oldAccount);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.smsLogin(request, "127.0.0.1"));
        assertEquals(UserCode.PHONE_UNVERIFIED, exception.getRecode());
    }

    @Test
    void verifiedNewPhoneReplacesCurrentPhone() {
        UserContextHolder.set(AuthenticatedUser.builder().userId(99L).sessionId("session").build());
        try {
        SmsVerifyDto request = SmsVerifyDto.builder().phone("13900139000").code("654321").build();
        UserAuthEntity account = UserAuthEntity.builder()
                .id(99L)
                .status(UserAccountStatus.NORMAL.getCode())
                .phone("+8613800138000")
                .build();
        when(authMapper.selectById(99L)).thenReturn(account);
        when(authMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        service.bindPhone(request);

        verify(authMapper).update(isNull(), any(LambdaUpdateWrapper.class));
        } finally {
            UserContextHolder.clear();
        }
    }

    @Test
    void consumedBindingChallengeCannotBeReplayed() {
        UserContextHolder.set(AuthenticatedUser.builder().userId(99L).sessionId("session").build());
        try {
        SmsVerifyDto request = SmsVerifyDto.builder().phone("13900139000").code("654321").build();
        UserAuthEntity account = UserAuthEntity.builder()
                .id(99L)
                .status(UserAccountStatus.NORMAL.getCode())
                .phone("+8613800138000")
                .build();
        when(authMapper.selectById(99L)).thenReturn(account);

        doThrow(new BusinessException(UserSmsCode.CODE_EXPIRED)).when(userSmsCodeService)
                .verifyBindCode("13900139000", "654321");
        BusinessException exception = assertThrows(BusinessException.class, () -> service.bindPhone(request));
        assertEquals(UserSmsCode.CODE_EXPIRED, exception.getRecode());
        } finally {
            UserContextHolder.clear();
        }
    }
}
