package org.ninestick.codekin.user.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import jakarta.validation.Validation;
import java.time.LocalDate;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.model.common.CommonCode;
import org.ninestick.codekin.utils.JsonUtils;
import org.ninestick.codekin.security.AuthenticatedUser;
import org.ninestick.codekin.security.UserContextHolder;
import org.ninestick.codekin.user.domain.dto.info.ProfileUpdateDto;
import org.ninestick.codekin.user.domain.entity.info.UserAuthEntity;
import org.ninestick.codekin.user.domain.entity.info.UserProfileEntity;
import org.ninestick.codekin.user.domain.enums.UserAccountStatus;
import org.ninestick.codekin.user.domain.enums.UserCode;
import org.ninestick.codekin.user.mapper.UserAuthMapper;
import org.ninestick.codekin.user.mapper.UserProfileMapper;
import org.ninestick.codekin.user.service.IUserAvatarService;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 基础资料更新的当前身份边界、选填字段清空以及请求校验。
 *
 * @author ninestick
 */
class UserServiceImplProfileTest {
    /** 被测用户业务。 */
    private UserServiceImpl service;
    /** 被模拟的账号 Mapper。 */
    private UserAuthMapper authMapper;
    /** 被模拟的资料 Mapper。 */
    private UserProfileMapper profileMapper;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "profile-test"), UserProfileEntity.class);
        service = new UserServiceImpl();
        authMapper = mock(UserAuthMapper.class);
        profileMapper = mock(UserProfileMapper.class);
        ReflectionTestUtils.setField(service, "authMapper", authMapper);
        ReflectionTestUtils.setField(service, "profileMapper", profileMapper);
        ReflectionTestUtils.setField(service, "userAvatarService", mock(IUserAvatarService.class));
        when(authMapper.selectById(42L)).thenReturn(UserAuthEntity.builder().id(42L).status(UserAccountStatus.NORMAL.getCode()).build());
        UserContextHolder.set(AuthenticatedUser.builder().userId(42L).sessionId("test-session").build());
    }

    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    /** 密码状态字段使用稳定 JSON 名称，响应不包含密码散列。 */
    @Test
    void myProfileReportsPasswordPresenceWithoutExposingHash() {
        when(profileMapper.selectById(42L)).thenReturn(UserProfileEntity.builder().userId(42L).nickname("昵称").build());
        String unset = JsonUtils.toJson(service.getMe());
        assertTrue(unset.contains("\"hasPassword\":false"));
        when(authMapper.selectById(42L)).thenReturn(UserAuthEntity.builder().id(42L)
                .status(UserAccountStatus.NORMAL.getCode()).passwordHash("private-password-hash").build());
        String configured = JsonUtils.toJson(service.getMe());
        assertTrue(configured.contains("\"hasPassword\":true"));
        assertFalse(configured.contains("private-password-hash"));
        assertFalse(configured.contains("passwordHash"));
    }

    @Test
    void anonymousCannotUpdateAnyProfile() {
        UserContextHolder.clear();
        assertEquals(CommonCode.UNAUTHORIZED, assertThrows(BusinessException.class,
                () -> service.updateProfile(ProfileUpdateDto.builder().nickname("昵称").build())).getRecode());
        verifyNoInteractions(authMapper, profileMapper);
    }

    @Test
    void unavailableAccountCannotWriteProfile() {
        when(authMapper.selectById(42L)).thenReturn(null);
        assertEquals(UserCode.ACCOUNT_UNAVAILABLE, assertThrows(BusinessException.class,
                () -> service.updateProfile(ProfileUpdateDto.builder().nickname("昵称").build())).getRecode());
        verifyNoInteractions(profileMapper);
    }

    @Test
    void updateTargetsCurrentUserAndExplicitlyClearsOptionalFields() {
        when(profileMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenAnswer(invocation -> {
            LambdaUpdateWrapper<UserProfileEntity> update = invocation.getArgument(1);
            assertTrue(update.getSqlSegment().contains("user_id ="));
            assertTrue(update.getParamNameValuePairs().containsValue(42L));
            assertTrue(update.getParamNameValuePairs().containsValue("昵称"));
            assertEquals(4, update.getParamNameValuePairs().values().stream().filter(value -> value == null).count());
            assertFalse(update.getSqlSet().contains("avatar_key"));
            return 1;
        });
        service.updateProfile(ProfileUpdateDto.builder().nickname(" 昵称 ").bio("  ").realName("").build());
    }

    @Test
    void missingProfileDoesNotReportSuccess() {
        when(profileMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);
        assertEquals(UserCode.PROFILE_UPDATE_NOT_COMPLETED, assertThrows(BusinessException.class,
                () -> service.updateProfile(ProfileUpdateDto.builder().nickname("昵称").build())).getRecode());
    }

    @Test
    void rejectsBlankNicknameOversizedBioAndFutureBirthday() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(ProfileUpdateDto.builder().nickname("昵称").build()).isEmpty());
            var violations = validator.validate(ProfileUpdateDto.builder().nickname(" ")
                    .bio("a".repeat(501)).birthDate(LocalDate.now().plusDays(1)).build());
            assertEquals(3, violations.size());
        }
    }
}
