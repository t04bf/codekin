package org.ninestick.codekin.user.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.ninestick.codekin.dubbo.api.file.IFileApi;
import org.ninestick.codekin.model.common.*;
import org.ninestick.codekin.model.file.dto.ReadyFileDto;
import org.ninestick.codekin.security.*;
import org.ninestick.codekin.user.domain.dto.info.AvatarUpdateDto;
import org.ninestick.codekin.user.domain.entity.info.*;
import org.ninestick.codekin.user.domain.enums.*;
import org.ninestick.codekin.user.mapper.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import org.ninestick.codekin.user.config.UserAvatarProperties;
import org.ninestick.codekin.user.domain.dto.info.DefaultAvatarUpdateDto;

/**
 * 头像只能引用当前用户已确认的 AVATAR 文件；无核验结果时不写数据库。
 *
 * @author ninestick
 */
class UserAvatarServiceImplTest {
    /** 被测头像业务。 */
    private UserAvatarServiceImpl service;
    /** 模拟内部文件契约。 */
    private IFileApi fileApi;
    /** 模拟用户资料持久化。 */
    private UserProfileMapper profiles;
    /** 模拟账号状态。 */
    private UserAuthMapper accounts;
    /** 预置头像配置。 */
    private UserAvatarProperties properties;
    /** 白名单对象键。 */
    private static final String FIRST = "uploads/avatar/default/avatar-01-wink.png";
    private static final String SECOND = "uploads/avatar/default/avatar-02-glasses.png";

    @BeforeEach
    void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "avatar-test"), UserProfileEntity.class);
        service = new UserAvatarServiceImpl();
        properties = new UserAvatarProperties();
        properties.setDefaults(List.of(
                UserAvatarProperties.DefaultAvatar.builder().name("眨眼").objectKey(FIRST).build(),
                UserAvatarProperties.DefaultAvatar.builder().name("眼镜").objectKey(SECOND).build()));
        ReflectionTestUtils.setField(service, "properties", properties);
        fileApi = mock(IFileApi.class);
        profiles = mock(UserProfileMapper.class);
        accounts = mock(UserAuthMapper.class);
        ReflectionTestUtils.setField(service, "fileApi", fileApi);
        ReflectionTestUtils.setField(service, "profileMapper", profiles);
        ReflectionTestUtils.setField(service, "authMapper", accounts);
        when(accounts.selectById(42L)).thenReturn(UserAuthEntity.builder().id(42L).status(UserAccountStatus.NORMAL.getCode()).build());
        UserContextHolder.set(AuthenticatedUser.builder().userId(42L).sessionId("test").build());
    }

    @AfterEach
    void clear() { UserContextHolder.clear(); }

    @Test
    void anonymousCannotSetAvatar() {
        UserContextHolder.clear();
        assertEquals(CommonCode.UNAUTHORIZED, assertThrows(BusinessException.class,
                () -> service.updateAvatar(AvatarUpdateDto.builder().fileId("99").build())).getRecode());
        verifyNoInteractions(fileApi, accounts, profiles);
    }

    @Test
    void missingOrForeignFileCannotBeUsed() {
        var request = AvatarUpdateDto.builder().fileId("99").build();
        when(fileApi.getReadyFile("99", 42L, "AVATAR")).thenReturn(RpcResult.failure(CommonCode.NOT_FOUND));
        assertEquals(UserCode.AVATAR_FILE_NOT_READY, assertThrows(BusinessException.class,
                () -> service.updateAvatar(request)).getRecode());
        when(fileApi.getReadyFile("99", 42L, "AVATAR")).thenReturn(RpcResult.success(ReadyFileDto.builder().ownerUserId(43L)
                .fileId("99").typeCode("AVATAR").objectKey("files/avatar/43/99.png").build()));
        assertEquals(UserCode.AVATAR_FILE_NOT_READY, assertThrows(BusinessException.class,
                () -> service.updateAvatar(request)).getRecode());
        verifyNoInteractions(profiles);
    }

    @Test
    void rpcFailureCannotBypassVerification() {
        when(fileApi.getReadyFile("99", 42L, "AVATAR")).thenThrow(new IllegalStateException("offline"));
        assertEquals(UserCode.AVATAR_SERVICE_UNAVAILABLE, assertThrows(BusinessException.class,
                () -> service.updateAvatar(AvatarUpdateDto.builder().fileId("99").build())).getRecode());
        verifyNoInteractions(profiles);
    }

    @Test
    void onlyCurrentUserIsUpdatedAfterVerification() {
        when(fileApi.getReadyFile("99", 42L, "AVATAR")).thenReturn(RpcResult.success(ReadyFileDto.builder().fileId("99").ownerUserId(42L)
                .typeCode("AVATAR").objectKey("files/avatar/42/99.png").build()));
        when(profiles.update(any(LambdaUpdateWrapper.class))).thenAnswer(invocation -> {
            LambdaUpdateWrapper<UserProfileEntity> update = invocation.getArgument(0);
            assertTrue(update.getSqlSegment().contains("user_id"));
            assertTrue(update.getParamNameValuePairs().containsValue(42L));
            assertTrue(update.getParamNameValuePairs().containsValue("files/avatar/42/99.png"));
            return 1;
        });
        service.updateAvatar(AvatarUpdateDto.builder().fileId("99").build());
        verify(profiles, times(1)).update(any(LambdaUpdateWrapper.class));
    }

    @Test
    void failedRpcResultCannotUsePartialData() {
        when(fileApi.getReadyFile("99", 42L, "AVATAR")).thenReturn(RpcResult.<ReadyFileDto>builder()
                .code(RpcCode.CALL_FAILED.getCode()).message("调用超时")
                .data(ReadyFileDto.builder().ownerUserId(42L).typeCode("AVATAR").objectKey("files/avatar/42/99.png").build()).build());
        assertEquals(UserCode.AVATAR_SERVICE_UNAVAILABLE, assertThrows(BusinessException.class,
                () -> service.updateAvatar(AvatarUpdateDto.builder().fileId("99").build())).getRecode());
        verifyNoInteractions(profiles);
    }

    @Test
    void failedAvatarUrlLookupFallsBackAndSuccessfulLookupUsesData() {
        when(fileApi.getAvatarUrls(java.util.List.of("files/avatar/42/99.png")))
                .thenReturn(RpcResult.failure(RpcCode.CALL_FAILED));
        assertNull(service.getAvatarUrl("files/avatar/42/99.png"));
        when(fileApi.getAvatarUrls(java.util.List.of("files/avatar/42/99.png")))
                .thenReturn(RpcResult.success(java.util.Map.of("files/avatar/42/99.png", "signed-url")));
        assertEquals("signed-url", service.getAvatarUrl("files/avatar/42/99.png"));
    }

    @Test
    void defaultListPreservesOrderWithOneRpcAndNoDatabase() {
        when(fileApi.getDefaultAvatarUrls(List.of(FIRST, SECOND)))
                .thenReturn(RpcResult.success(Map.of(FIRST, "first-signed", SECOND, "second-signed")));
        var result = service.getDefaultAvatars();
        assertEquals(List.of(FIRST, SECOND), result.stream().map(item -> item.getObjectKey()).toList());
        assertEquals("眨眼", result.getFirst().getName());
        assertEquals("first-signed", result.getFirst().getUrl());
        verify(fileApi, times(1)).getDefaultAvatarUrls(List.of(FIRST, SECOND));
        verifyNoInteractions(accounts, profiles);
    }

    @Test
    void signingFailureAndIncompleteBatchCannotProduceOptions() {
        when(fileApi.getDefaultAvatarUrls(anyList())).thenReturn(RpcResult.<Map<String, String>>builder()
                .code(RpcCode.CALL_FAILED.getCode()).data(Map.of(FIRST, "partial")).build());
        assertEquals(UserCode.AVATAR_SERVICE_UNAVAILABLE,
                assertThrows(BusinessException.class, service::getDefaultAvatars).getRecode());
        when(fileApi.getDefaultAvatarUrls(anyList())).thenReturn(RpcResult.success(Map.of(FIRST, "partial")));
        assertEquals(UserCode.AVATAR_SERVICE_UNAVAILABLE,
                assertThrows(BusinessException.class, service::getDefaultAvatars).getRecode());
        verifyNoInteractions(profiles);
    }

    @Test
    void selectionStoresOnlyStableKeyForCurrentUserWithoutRpc() {
        when(profiles.update(any(LambdaUpdateWrapper.class))).thenAnswer(invocation -> {
            LambdaUpdateWrapper<UserProfileEntity> update = invocation.getArgument(0);
            assertTrue(update.getSqlSegment().contains("user_id"));
            assertTrue(update.getParamNameValuePairs().containsValue(42L));
            assertTrue(update.getParamNameValuePairs().containsValue(FIRST));
            return 1;
        });
        service.updateDefaultAvatar(DefaultAvatarUpdateDto.builder().objectKey(FIRST).build());
        verify(profiles, times(1)).update(any(LambdaUpdateWrapper.class));
        verifyNoInteractions(fileApi);
    }

    @Test
    void arbitraryUrlsAndUnlistedKeysCannotBeSelected() {
        for (String key : List.of("https://example.com/avatar.png", "uploads/avatar/default/not-listed.png",
                "files/avatar/42/99.png")) {
            assertEquals(UserCode.DEFAULT_AVATAR_INVALID, assertThrows(BusinessException.class,
                    () -> service.updateDefaultAvatar(DefaultAvatarUpdateDto.builder().objectKey(key).build())).getRecode());
        }
        verifyNoInteractions(fileApi, accounts, profiles);
    }

    @Test
    void selectionRequiresIdentityAndActiveAccount() {
        UserContextHolder.clear();
        assertEquals(CommonCode.UNAUTHORIZED, assertThrows(BusinessException.class,
                () -> service.updateDefaultAvatar(DefaultAvatarUpdateDto.builder().objectKey(FIRST).build())).getRecode());
        UserContextHolder.set(AuthenticatedUser.builder().userId(42L).sessionId("test").build());
        when(accounts.selectById(42L)).thenReturn(null);
        assertEquals(UserCode.ACCOUNT_UNAVAILABLE, assertThrows(BusinessException.class,
                () -> service.updateDefaultAvatar(DefaultAvatarUpdateDto.builder().objectKey(FIRST).build())).getRecode());
        verifyNoInteractions(fileApi, profiles);
    }

    @Test
    void randomAssignmentUsesConfiguredKeysWithoutNetworkAndRejectsEmptyCatalog() {
        for (int i = 0; i < 20; i++) assertTrue(List.of(FIRST, SECOND).contains(service.pickRandomDefaultAvatarKey()));
        properties.setDefaults(List.of());
        assertEquals(UserCode.DEFAULT_AVATAR_UNAVAILABLE,
                assertThrows(BusinessException.class, service::pickRandomDefaultAvatarKey).getRecode());
        verifyNoInteractions(fileApi, accounts, profiles);
    }

    @Test
    void existingDefaultUsesSpecialSigningEvenWhenRemovedFromChoices() {
        properties.setDefaults(List.of());
        when(fileApi.getDefaultAvatarUrls(List.of(FIRST))).thenReturn(RpcResult.success(Map.of(FIRST, "signed")));
        assertEquals("signed", service.getAvatarUrl(FIRST));
        verify(fileApi, never()).getAvatarUrls(anyList());
        verifyNoInteractions(profiles);
    }
}
