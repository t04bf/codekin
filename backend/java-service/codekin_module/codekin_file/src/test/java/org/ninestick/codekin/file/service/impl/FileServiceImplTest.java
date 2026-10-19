package org.ninestick.codekin.file.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.ninestick.codekin.model.file.enums.UploadType;
import org.ninestick.codekin.file.config.FileStorageProperties;
import org.ninestick.codekin.file.domain.dto.UploadSignatureDto;
import org.ninestick.codekin.file.domain.entity.FileEntity;
import org.ninestick.codekin.file.domain.enums.*;
import org.ninestick.codekin.file.mapper.FileMapper;
import org.ninestick.codekin.file.storage.*;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.redis.RedisService;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 上传所有权、签名参数、并发状态和批量访问查询边界。
 *
 * @author ninestick
 */
class FileServiceImplTest {
    /** 被测服务。 */
    private FileServiceImpl service;
    /** 隔离 SQL。 */
    private FileMapper mapper;
    /** 隔离真实对象存储。 */
    private S3Storage storage;
    /** 隔离 Redis。 */
    private RedisService redis;
    /** 验证不必要的解码没有发生。 */
    private AvatarImageProcessor processor;

    @BeforeEach
    void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "file-test"), FileEntity.class);
        service = new FileServiceImpl();
        mapper = mock(FileMapper.class);
        storage = mock(S3Storage.class);
        redis = mock(RedisService.class);
        processor = mock(AvatarImageProcessor.class);
        var properties = new FileStorageProperties();
        properties.setPublicEndpoint(URI.create("http://127.0.0.1:8333"));
        properties.setBucket("codekin");
        ReflectionTestUtils.setField(service, "fileMapper", mapper);
        ReflectionTestUtils.setField(service, "storage", storage);
        ReflectionTestUtils.setField(service, "redisService", redis);
        ReflectionTestUtils.setField(service, "imageProcessor", processor);
        ReflectionTestUtils.setField(service, "properties", properties);
    }

    @ParameterizedTest
    @EnumSource(UploadType.class)
    void unknownTypeFalseMimeAndSizeAreRejectedBeforeQuotaOrStorage(UploadType type) {
        assertEquals(FileCode.INVALID_TYPE, assertThrows(BusinessException.class, () -> service.createSignature(
                UploadSignatureDto.builder().typeCode("UNKNOWN").fileName("a.png").size(1L).contentType("image/png").build(), 42L)).getRecode());
        assertEquals(FileCode.INVALID_FORMAT, assertThrows(BusinessException.class, () -> service.createSignature(
                UploadSignatureDto.builder().typeCode(type.getCode()).fileName("a.png").size(1L).contentType("image/jpeg").build(), 42L)).getRecode());
        assertEquals(FileCode.SIZE_LIMIT, assertThrows(BusinessException.class, () -> service.createSignature(
                UploadSignatureDto.builder().typeCode(type.getCode()).fileName("a.png").size(5242881L).contentType("image/png").build(), 42L)).getRecode());
        verifyNoInteractions(redis, storage, mapper);
    }

    @ParameterizedTest
    @EnumSource(UploadType.class)
    void signatureUsesServerPathAndPersistsOwner(UploadType type) {
        when(redis.incrementWithinLimit(anyString(), anyLong(), any())).thenReturn(true);
        when(storage.createPostFields(anyString(), anyString(), anyLong(), any())).thenReturn(Map.of("policy", "signed"));
        when(mapper.insert(any(FileEntity.class))).thenAnswer(invocation -> {
            FileEntity file = invocation.getArgument(0);
            assertEquals(42L, file.getOwnerUserId());
            assertEquals(type.getCode(), file.getTypeCode());
            assertTrue(file.getTemporaryKey().startsWith("uploads/" + type.getCode().toLowerCase(Locale.ROOT) + "/42/"));
            assertFalse(file.getTemporaryKey().contains("untrusted"));
            file.setId(99L);
            return 1;
        });
        var result = service.createSignature(UploadSignatureDto.builder().typeCode(type.getCode())
                .fileName("../untrusted.PNG").contentType("image/png").size(100L).build(), 42L);
        assertEquals("99", result.getFileId());
        assertEquals("http://127.0.0.1:8333/codekin", result.getUploadUrl());
        verify(redis).incrementWithinLimit(eq(FileStorageConstants.SIGNATURE_RATE_PREFIX + type.getCode() + ":42"),
                eq((long) type.getSignatureLimit()), any());
    }

    @Test
    void anotherOwnersFileCannotBeCompleted() {
        when(mapper.selectOne(any())).thenReturn(null);
        assertEquals(FileCode.NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.complete(99L, 42L)).getRecode());
        verifyNoInteractions(storage, processor);
    }

    @Test
    void readyFileConfirmationIsIdempotent() {
        when(mapper.selectOne(any())).thenReturn(FileEntity.builder().id(99L).ownerUserId(42L).typeCode("AVATAR")
                .status(FileStatus.READY.getCode()).objectKey("files/avatar/42/99.png")
                .actualSize(100L).contentType("image/png").build());
        assertEquals("files/avatar/42/99.png", service.complete(99L, 42L).getObjectKey());
        verifyNoInteractions(storage, processor);
        verify(mapper, never()).update(any(LambdaUpdateWrapper.class));
    }

    @Test
    void concurrentConfirmationDoesNotStartAnotherDecoder() {
        when(mapper.selectOne(any())).thenReturn(FileEntity.builder().id(99L).ownerUserId(42L).typeCode("AVATAR")
                .status(FileStatus.UPLOADING.getCode()).expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5)).build());
        when(mapper.update(any(LambdaUpdateWrapper.class))).thenReturn(0);
        assertEquals(FileCode.NOT_READY, assertThrows(BusinessException.class,
                () -> service.complete(99L, 42L)).getRecode());
        verifyNoInteractions(storage, processor);
    }

    @Test
    void exhaustedProcessingBudgetRejectsBeforeStorageReadAndAllowsRetry() {
        when(mapper.selectOne(any())).thenReturn(FileEntity.builder().id(99L).ownerUserId(42L).typeCode("AVATAR")
                .status(FileStatus.UPLOADING.getCode()).expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5)).build());
        when(mapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);
        ReflectionTestUtils.setField(service, "processingSlots", new java.util.concurrent.Semaphore(0));
        assertEquals(FileCode.PROCESSING_BUSY, assertThrows(BusinessException.class,
                () -> service.complete(99L, 42L)).getRecode());
        verifyNoInteractions(storage, processor);
        verify(mapper, times(2)).update(any(LambdaUpdateWrapper.class));
    }

    @Test
    void avatarUrlsUseOneDatabaseQueryForWholeBatch() {
        when(mapper.selectList(any())).thenReturn(List.of(FileEntity.builder().objectKey("files/avatar/42/99.png").build(),
                FileEntity.builder().objectKey("files/avatar/43/100.png").build()));
        when(storage.getAccessUrl(anyString())).thenReturn("signed-url");
        assertEquals(2, service.getAvatarUrls(List.of("files/avatar/42/99.png", "files/avatar/43/100.png")).size());
        verify(mapper, times(1)).selectList(any());
        verify(mapper, never()).selectOne(any());
    }

    @Test
    void publicAvatarSigningKeepsItsAvatarOnlyFilterForPrivateResumePhotos() {
        when(mapper.selectList(any())).thenAnswer(invocation -> {
            var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FileEntity>) invocation.getArgument(0);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("type_code ="));
            assertTrue(query.getParamNameValuePairs().containsValue(UploadType.AVATAR.getCode()));
            assertFalse(query.getParamNameValuePairs().containsValue(UploadType.RESUME_PHOTO.getCode()));
            return List.of();
        });
        assertTrue(service.getAvatarUrls(List.of("files/resume_photo/42/99.png")).isEmpty());
        verifyNoInteractions(storage);
    }

    @ParameterizedTest
    @EnumSource(UploadType.class)
    void verifiedFileUsesIndependentKeyAndInvalidSourceIsRejected(UploadType type) {
        String namespace = type.getCode().toLowerCase(Locale.ROOT);
        String readyKey = "files/" + namespace + "/42/99.png";
        FileEntity file = FileEntity.builder().id(99L).ownerUserId(42L).typeCode(type.getCode())
                .status(FileStatus.UPLOADING.getCode()).temporaryKey("uploads/" + namespace + "/42/source.png").declaredSize(3L)
                .declaredContentType("image/png").expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5)).build();
        when(mapper.selectOne(any())).thenReturn(file);
        when(mapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(storage.readUpload(anyString(), anyLong())).thenReturn(new byte[]{1, 2, 3});
        when(processor.process(any(), anyString(), any())).thenReturn(new byte[]{4, 5});
        assertEquals(readyKey, service.complete(99L, 42L).getObjectKey());
        verify(processor).process(any(), eq("image/png"), eq(type));
        verify(storage).writePng(eq(readyKey), any());
        verify(storage, never()).writePng(eq(file.getTemporaryKey()), any());
    }

    @Test
    void defaultResourcesNeedNoUploadRecords() {
        var keys = List.of("uploads/avatar/default/avatar-01-wink.png", "uploads/avatar/default/avatar-02-glasses.png");
        when(storage.getAccessUrl(anyString())).thenAnswer(invocation -> "signed:" + invocation.getArgument(0));
        var result = service.getDefaultAvatarUrls(keys);
        assertEquals(2, result.size());
        assertEquals("signed:" + keys.getFirst(), result.get(keys.getFirst()));
        verify(storage, times(2)).getAccessUrl(anyString());
        verifyNoInteractions(mapper, redis, processor);
    }

    @Test
    void defaultSigningRejectsOtherNamespacesTraversalUrlsAndUnboundedBatches() {
        for (String key : List.of("files/avatar/42/99.png", "uploads/avatar/42/source.png",
                "uploads/avatar/default/../private.png", "uploads/avatar/default/sub/a.png",
                "http://seaweedfs:8333/codekin/uploads/avatar/default/a.png", "uploads/avatar/default/a.jpg")) {
            assertThrows(BusinessException.class, () -> service.getDefaultAvatarUrls(List.of(key)));
        }
        assertThrows(BusinessException.class, () -> service.getDefaultAvatarUrls(null));
        assertThrows(BusinessException.class, () -> service.getDefaultAvatarUrls(List.of()));
        assertThrows(BusinessException.class, () -> service.getDefaultAvatarUrls(Arrays.asList((String) null)));
        assertThrows(BusinessException.class, () -> service.getDefaultAvatarUrls(
                Collections.nCopies(101, "uploads/avatar/default/a.png")));
        verifyNoInteractions(storage, mapper, redis, processor);
    }
    @Test void privatePhotoUrlUsesOwnerPurposeReadyAndTwoHourExpiry() {
        var file = FileEntity.builder().id(99L).ownerUserId(42L).typeCode("RESUME_PHOTO")
                .status("READY").objectKey("files/resume-photo/42/99.png").actualSize(5L).contentType("image/png").build();
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class))).thenAnswer(call -> {
            var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>)call.getArgument(0);
            assertTrue(query.getSqlSegment().contains("owner_user_id"));
            assertTrue(query.getSqlSegment().contains("type_code"));
            assertTrue(query.getSqlSegment().contains("status"));
            assertTrue(query.getParamNameValuePairs().containsValue(42L));
            assertTrue(query.getParamNameValuePairs().containsValue("RESUME_PHOTO"));
            assertTrue(query.getParamNameValuePairs().containsValue("READY"));
            return file;
        });
        when(storage.getAccessUrl(file.getObjectKey(), Duration.ofHours(2))).thenReturn("https://signed.example/photo");
        var before = Instant.now();
        var result = service.getResumePhotoUrl(99L, 42L);
        assertEquals("https://signed.example/photo", result.getUrl());
        assertTrue(result.getExpiresAt().isAfter(before.plusSeconds(7199)));
        verify(mapper, times(1)).selectOne(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class));
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class))).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getResumePhotoUrl(100L, 42L));
        verify(storage, times(1)).getAccessUrl(anyString(), any(Duration.class));
    }

    @Test void resumePhotoBatchUsesOneOwnerScopedQueryAndTwoHourLocalSignatures() {
        var first = FileEntity.builder().id(99L).objectKey("ready/resume_photo/42/99.png").build();
        var second = FileEntity.builder().id(100L).objectKey("ready/resume_photo/42/100.png").build();
        when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class))).thenAnswer(call -> {
            var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) call.getArgument(0);
            assertTrue(query.getSqlSegment().contains("owner_user_id"));
            assertTrue(query.getSqlSegment().contains("type_code"));
            assertTrue(query.getSqlSegment().contains("status"));
            assertTrue(query.getParamNameValuePairs().containsValue(42L));
            assertTrue(query.getParamNameValuePairs().containsValue(UploadType.RESUME_PHOTO.getCode()));
            assertTrue(query.getParamNameValuePairs().containsValue(FileStatus.READY.getCode()));
            return List.of(first, second);
        });
        when(storage.getAccessUrl(anyString(), eq(Duration.ofHours(2)))).thenAnswer(call -> "https://signed.example/" + call.getArgument(0));
        var before = Instant.now();
        var result = service.getResumePhotoUrls(List.of(99L, 100L, 99L), 42L);
        assertEquals(Set.of("99", "100"), result.keySet());
        assertEquals("https://signed.example/" + first.getObjectKey(), result.get("99").getUrl());
        assertTrue(result.get("99").getExpiresAt().isAfter(before.plusSeconds(7199)));
        assertEquals(result.get("99").getExpiresAt(), result.get("100").getExpiresAt());
        verify(mapper, times(1)).selectList(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class));
        verify(storage, times(2)).getAccessUrl(anyString(), eq(Duration.ofHours(2)));
        verifyNoMoreInteractions(mapper, storage);
    }

    @Test void missingOrUnauthorizedBatchReferenceRejectsBeforeAnySignature() {
        when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class)))
                .thenReturn(List.of(FileEntity.builder().id(99L).objectKey("ready/resume_photo/42/99.png").build()));
        assertThrows(BusinessException.class, () -> service.getResumePhotoUrls(List.of(99L, 100L), 42L));
        verifyNoInteractions(storage);
    }

    @Test void emptyAndInvalidPhotoBatchesNeverQueryOrSign() {
        assertEquals(Map.of(), service.getResumePhotoUrls(List.of(), 42L));
        assertThrows(BusinessException.class, () -> service.getResumePhotoUrls(Collections.nCopies(101, 99L), 42L));
        assertThrows(BusinessException.class, () -> service.getResumePhotoUrls(List.of(99L), 0L));
        assertThrows(BusinessException.class, () -> service.getResumePhotoUrls(List.of(0L), 42L));
        verifyNoInteractions(mapper, storage);
    }
}
