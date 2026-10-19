package org.ninestick.codekin.user.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.model.common.CommonCode;
import org.ninestick.codekin.security.AuthenticatedUser;
import org.ninestick.codekin.security.UserContextHolder;
import org.ninestick.codekin.user.domain.dto.info.EmailSendDto;
import org.ninestick.codekin.user.domain.dto.info.EmailVerifyDto;
import org.ninestick.codekin.user.domain.entity.info.UserAuthEntity;
import org.ninestick.codekin.user.domain.enums.UserAccountStatus;
import org.ninestick.codekin.user.domain.enums.UserCode;
import org.ninestick.codekin.user.domain.enums.UserEmailCode;
import org.ninestick.codekin.user.mapper.UserAuthMapper;
import org.ninestick.codekin.user.service.IUserEmailCodeService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 邮箱换绑的认证边界、占用检查和数据库并发更新保护。
 *
 * @author ninestick
 */
class UserServiceImplEmailBindTest {
    /** 被测业务。 */
    private UserServiceImpl service;
    /** 认证信息持久化。 */
    private UserAuthMapper mapper;
    /** 绑定验证码服务。 */
    private IUserEmailCodeService codes;
    /** 当前账号快照。 */
    private UserAuthEntity auth;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "email-bind-test"),
                UserAuthEntity.class);
        service = new UserServiceImpl();
        mapper = mock(UserAuthMapper.class);
        codes = mock(IUserEmailCodeService.class);
        ReflectionTestUtils.setField(service, "authMapper", mapper);
        ReflectionTestUtils.setField(service, "userEmailCodeService", codes);
        auth = UserAuthEntity.builder().id(42L).status(UserAccountStatus.NORMAL.getCode())
                .email("old@example.com").build();
        when(mapper.selectById(42L)).thenReturn(auth);
        UserContextHolder.set(AuthenticatedUser.builder().userId(42L).sessionId("session").build());
    }

    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    @Test
    void bothOperationsRequireLogin() {
        UserContextHolder.clear();
        assertEquals(CommonCode.UNAUTHORIZED, assertThrows(BusinessException.class,
                () -> service.sendBindEmailCode(EmailSendDto.builder().email("new@example.com").build()))
                .getRecode());
        assertEquals(CommonCode.UNAUTHORIZED, assertThrows(BusinessException.class,
                () -> service.bindEmail(EmailVerifyDto.builder().email("new@example.com").code("123456").build()))
                .getRecode());
        verifyNoInteractions(codes);
    }

    @Test
    void occupiedEmailIsRejectedBeforeSending() {
        when(mapper.exists(any(LambdaQueryWrapper.class))).thenReturn(true);
        assertEquals(UserCode.EMAIL_ALREADY_BOUND, assertThrows(BusinessException.class,
                () -> service.sendBindEmailCode(EmailSendDto.builder().email("new@example.com").build()))
                .getRecode());
        verifyNoInteractions(codes);
    }

    @Test
    void sameEmailIsRejectedForSendingAndBinding() {
        assertEquals(UserCode.EMAIL_UNCHANGED, assertThrows(BusinessException.class,
                () -> service.sendBindEmailCode(EmailSendDto.builder().email("OLD@EXAMPLE.COM").build()))
                .getRecode());
        assertEquals(UserCode.EMAIL_UNCHANGED, assertThrows(BusinessException.class,
                () -> service.bindEmail(EmailVerifyDto.builder().email("OLD@EXAMPLE.COM").code("123456").build()))
                .getRecode());
        verifyNoInteractions(codes);
    }

    @Test
    void unavailableAccountCannotSendOrBind() {
        when(mapper.selectById(42L)).thenReturn(null);
        assertEquals(UserCode.ACCOUNT_UNAVAILABLE, assertThrows(BusinessException.class,
                () -> service.sendBindEmailCode(EmailSendDto.builder().email("new@example.com").build()))
                .getRecode());
        assertEquals(UserCode.ACCOUNT_UNAVAILABLE, assertThrows(BusinessException.class,
                () -> service.bindEmail(EmailVerifyDto.builder().email("new@example.com").code("123456").build()))
                .getRecode());
        verifyNoInteractions(codes);
    }

    @Test
    void sendingUsesNormalizedAddressAndBindingPurpose() {
        service.sendBindEmailCode(EmailSendDto.builder().email("New@Example.Com").build());
        verify(codes).sendBindCode("new@example.com");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "old@example.com")
    void firstBindingAndRebindingUpdateOnlyCurrentAccountWithOldEmailGuard(String oldEmail) {
        auth.setEmail(oldEmail);
        when(mapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenAnswer(invocation -> {
            LambdaUpdateWrapper<UserAuthEntity> update = invocation.getArgument(1);
            String where = update.getSqlSegment();
            assertTrue(where.contains("id ="));
            assertTrue(where.contains("status ="));
            assertTrue(where.contains(oldEmail == null ? "email IS NULL" : "email ="));
            assertTrue(update.getSqlSet().contains("email_verified_at="));
            assertTrue(update.getParamNameValuePairs().containsValue(42L));
            assertTrue(update.getParamNameValuePairs().containsValue("new@example.com"));
            if (oldEmail != null) {
                assertTrue(update.getParamNameValuePairs().containsValue(oldEmail));
            }
            return 1;
        });
        service.bindEmail(EmailVerifyDto.builder().email("New@Example.Com").code("123456").build());
        verify(codes).verifyBindCode("new@example.com", "123456");
    }

    @Test
    void invalidCodeCannotUpdateDatabase() {
        doThrow(new BusinessException(UserEmailCode.CODE_INVALID)).when(codes)
                .verifyBindCode("new@example.com", "123456");
        assertEquals(UserEmailCode.CODE_INVALID, assertThrows(BusinessException.class,
                () -> service.bindEmail(EmailVerifyDto.builder().email("new@example.com").code("123456").build()))
                .getRecode());
        verify(mapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void concurrentEmailClaimUsesUniqueConstraintError() {
        when(mapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenThrow(new DuplicateKeyException("email"));
        assertEquals(UserCode.EMAIL_ALREADY_BOUND, assertThrows(BusinessException.class,
                () -> service.bindEmail(EmailVerifyDto.builder().email("new@example.com").code("123456").build()))
                .getRecode());
    }

    @Test
    void staleAccountSnapshotDoesNotReportSuccess() {
        when(mapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);
        assertEquals(UserCode.EMAIL_BIND_NOT_COMPLETED, assertThrows(BusinessException.class,
                () -> service.bindEmail(EmailVerifyDto.builder().email("new@example.com").code("123456").build()))
                .getRecode());
    }
}
