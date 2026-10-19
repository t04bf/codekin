package org.ninestick.codekin.file.service.impl;

import static org.ninestick.codekin.file.storage.FileStorageConstants.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.Semaphore;
import java.time.*;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.ninestick.codekin.file.config.FileStorageProperties;
import org.ninestick.codekin.file.domain.dto.UploadSignatureDto;
import org.ninestick.codekin.file.domain.entity.FileEntity;
import org.ninestick.codekin.file.domain.enums.*;
import org.ninestick.codekin.file.domain.vo.UploadSignatureVo;
import org.ninestick.codekin.file.mapper.FileMapper;
import org.ninestick.codekin.file.service.IFileService;
import org.ninestick.codekin.file.storage.*;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.model.common.CommonCode;
import org.ninestick.codekin.model.file.constants.DefaultAvatarConstants;
import org.ninestick.codekin.model.file.dto.ReadyFileDto;
import org.ninestick.codekin.model.file.dto.SignedFileUrlDto;
import org.ninestick.codekin.model.file.enums.UploadType;
import org.ninestick.codekin.redis.RedisService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 受限直传、实际图片审核及正式化；状态条件更新防止并发重复处理。
 *
 * @author ninestick
 */
@Slf4j
@Service
public class FileServiceImpl implements IFileService {
    /** 文件记录入口。 */
    @Resource private FileMapper fileMapper;
    /** S3 适配器。 */
    @Resource private S3Storage storage;
    /** Redis 基础操作，业务限流留在本服务。 */
    @Resource private RedisService redisService;
    /** 基础设施期限配置。 */
    @Resource private FileStorageProperties properties;
    /** 实际图片内容处理。 */
    @Resource private AvatarImageProcessor imageProcessor;

    /** 同时限制源文件读取与图片解码，避免高并发积压 byte 数组或解码图像。 */
    private Semaphore processingSlots = new Semaphore(2);

    /** 从 Nacos 配置初始化单实例处理预算。 */
    @PostConstruct
    public void initializeProcessingSlots() {
        processingSlots = new Semaphore(properties.getMaxConcurrentImageProcessing());
    }

    @Override
    public UploadSignatureVo createSignature(UploadSignatureDto request, Long ownerUserId) {
        UploadType type = UploadType.fromCode(request.getTypeCode());
        if (type == null) throw new BusinessException(FileCode.INVALID_TYPE);
        String extension = StringUtils.getFilenameExtension(request.getFileName());
        extension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        String expectedMime = type.getFormats().get(extension);
        if (expectedMime == null || !expectedMime.equals(request.getContentType())) {
            throw new BusinessException(FileCode.INVALID_FORMAT);
        }
        if (request.getSize() < 1 || request.getSize() > type.getMaxBytes()) {
            throw new BusinessException(FileCode.SIZE_LIMIT);
        }
        if (!redisService.incrementWithinLimit(SIGNATURE_RATE_PREFIX + type.getCode() + ":" + ownerUserId,
                type.getSignatureLimit(), properties.getSignatureWindow())) {
            throw new BusinessException(FileCode.RATE_LIMIT);
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getSignatureTtl());
        Instant confirmationExpiresAt = now.plus(properties.getConfirmationTtl());
        String temporaryKey = TEMPORARY_PREFIX + type.getCode().toLowerCase(Locale.ROOT) + "/" + ownerUserId
                + "/" + UUID.randomUUID() + "." + extension;
        var fields = storage.createPostFields(temporaryKey, expectedMime, type.getMaxBytes(), expiresAt);
        FileEntity entity = FileEntity.builder().ownerUserId(ownerUserId).typeCode(type.getCode())
                .temporaryKey(temporaryKey).declaredSize(request.getSize()).declaredContentType(expectedMime)
                .status(FileStatus.UPLOADING.getCode())
                .expiresAt(LocalDateTime.ofInstant(confirmationExpiresAt, ZoneOffset.UTC))
                .createdAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC))
                .updatedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC)).build();
        fileMapper.insert(entity);
        log.info("文件上传凭证已签发 fileId={} type={} userId={}", entity.getId(), type.getCode(), ownerUserId);
        return UploadSignatureVo.builder().fileId(entity.getId().toString())
                .uploadUrl(properties.getPublicEndpoint().toString().replaceAll("/+$", "") + "/" + properties.getBucket())
                .method(POST_METHOD).fields(fields).expiresAt(expiresAt).confirmationExpiresAt(confirmationExpiresAt).build();
    }

    @Override
    public ReadyFileDto complete(Long fileId, Long ownerUserId) {
        FileEntity file = fileMapper.selectOne(Wrappers.<FileEntity>lambdaQuery()
                .eq(FileEntity::getId, fileId).eq(FileEntity::getOwnerUserId, ownerUserId));
        if (file == null) throw new BusinessException(FileCode.NOT_FOUND);
        if (FileStatus.READY.getCode().equals(file.getStatus())) return toReadyFile(file);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (!file.getExpiresAt().isAfter(now)) throw new BusinessException(FileCode.EXPIRED);
        if (!FileStatus.UPLOADING.getCode().equals(file.getStatus())) throw new BusinessException(FileCode.NOT_READY);
        int acquired = fileMapper.update(Wrappers.<FileEntity>lambdaUpdate()
                .eq(FileEntity::getId, fileId).eq(FileEntity::getOwnerUserId, ownerUserId)
                .eq(FileEntity::getStatus, FileStatus.UPLOADING.getCode()).gt(FileEntity::getExpiresAt, now)
                .set(FileEntity::getStatus, FileStatus.PROCESSING.getCode()).set(FileEntity::getUpdatedAt, now));
        if (acquired != 1) throw new BusinessException(FileCode.NOT_READY);
        boolean processingAcquired = processingSlots.tryAcquire();
        try {
            if (!processingAcquired) throw new BusinessException(FileCode.PROCESSING_BUSY);
            UploadType type = UploadType.fromCode(file.getTypeCode());
            if (type == null) throw new BusinessException(FileCode.INVALID_TYPE);
            byte[] source = storage.readUpload(file.getTemporaryKey(), type.getMaxBytes());
            if (source.length != file.getDeclaredSize()) throw new BusinessException(FileCode.SIZE_LIMIT, "实际文件大小与签名申请不一致");
            byte[] output = imageProcessor.process(source, file.getDeclaredContentType(), type);
            // 独立正式对象，不复制可被原 POST 凭证继续覆盖的临时 key。
            String objectKey = READY_PREFIX + type.getCode().toLowerCase(Locale.ROOT) + "/" + ownerUserId
                    + "/" + fileId + ".png";
            storage.writePng(objectKey, output);
            int updated = fileMapper.update(Wrappers.<FileEntity>lambdaUpdate()
                    .eq(FileEntity::getId, fileId).eq(FileEntity::getStatus, FileStatus.PROCESSING.getCode())
                    .set(FileEntity::getObjectKey, objectKey).set(FileEntity::getActualSize, (long) output.length)
                    .set(FileEntity::getContentType, PNG_CONTENT_TYPE).set(FileEntity::getStatus, FileStatus.READY.getCode())
                    .set(FileEntity::getUpdatedAt, LocalDateTime.now(ZoneOffset.UTC)));
            if (updated != 1) throw new BusinessException(FileCode.NOT_READY);
            file.setObjectKey(objectKey);
            file.setActualSize((long) output.length);
            file.setContentType(PNG_CONTENT_TYPE);
            log.info("文件核验完成 fileId={} userId={}", fileId, ownerUserId);
            return toReadyFile(file);
        } catch (RuntimeException exception) {
            // 文件校验失败不可重新确认；网络或未上传完成允许重试，异常崩溃由过期清理兜底。
            boolean rejected = exception instanceof BusinessException business
                    && Set.of(FileCode.INVALID_IMAGE, FileCode.INVALID_FORMAT, FileCode.PIXEL_LIMIT,
                            FileCode.SIZE_LIMIT, FileCode.INVALID_TYPE).contains(business.getRecode());
            fileMapper.update(Wrappers.<FileEntity>lambdaUpdate().eq(FileEntity::getId, fileId)
                    .eq(FileEntity::getStatus, FileStatus.PROCESSING.getCode())
                    .set(FileEntity::getStatus, rejected ? FileStatus.REJECTED.getCode() : FileStatus.UPLOADING.getCode())
                    .set(FileEntity::getUpdatedAt, LocalDateTime.now(ZoneOffset.UTC)));
            throw exception;
        } finally {
            if (processingAcquired) processingSlots.release();
        }
    }

    @Override
    public ReadyFileDto getReadyFile(Long fileId, Long ownerUserId, String typeCode) {
        if (fileId == null || ownerUserId == null || UploadType.fromCode(typeCode) == null) return null;
        FileEntity file = fileMapper.selectOne(Wrappers.<FileEntity>lambdaQuery().eq(FileEntity::getId, fileId)
                .eq(FileEntity::getOwnerUserId, ownerUserId).eq(FileEntity::getTypeCode, typeCode)
                .eq(FileEntity::getStatus, FileStatus.READY.getCode()));
        return file == null ? null : toReadyFile(file);
    }

    @Override
    public Map<String, String> getAvatarUrls(List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) return Map.of();
        if (objectKeys.size() > 100) throw new IllegalArgumentException("头像查询最多 100 项");
        List<FileEntity> files = fileMapper.selectList(Wrappers.<FileEntity>lambdaQuery()
                .in(FileEntity::getObjectKey, objectKeys).eq(FileEntity::getTypeCode, UploadType.AVATAR.getCode())
                .eq(FileEntity::getStatus, FileStatus.READY.getCode()));
        Map<String, String> urls = new HashMap<>();
        // 一次 SQL 批量读取；GET 签名为 SDK 本地运算，不发起逐条对象存储查询。
        for (FileEntity file : files) urls.put(file.getObjectKey(), storage.getAccessUrl(file.getObjectKey()));
        return urls;
    }

    @Override
    public Map<String, String> getDefaultAvatarUrls(List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty() || objectKeys.size() > DefaultAvatarConstants.MAX_BATCH_SIZE
                || objectKeys.stream().anyMatch(key -> key == null || key.length() > 512
                        || !key.matches(DefaultAvatarConstants.OBJECT_KEY_PATTERN))) {
            throw new BusinessException(CommonCode.INVALID_ARGUMENT, "默认头像对象路径不正确");
        }
        Map<String, String> urls = new LinkedHashMap<>();
        // 默认资源由管理员部署；签名是 SDK 本地运算，不按图片逐条查询数据库或读取对象。
        for (String objectKey : objectKeys) urls.put(objectKey, storage.getAccessUrl(objectKey));
        return urls;
    }

    @Override
    public SignedFileUrlDto getResumePhotoUrl(Long fileId, Long ownerUserId) {
        var file = getReadyFile(fileId, ownerUserId, UploadType.RESUME_PHOTO.getCode());
        if (file == null) throw new BusinessException(CommonCode.NOT_FOUND);
        var ttl = Duration.ofHours(2);
        var expiresAt = Instant.now().plus(ttl);
        return SignedFileUrlDto.builder().url(storage.getAccessUrl(file.getObjectKey(), ttl)).expiresAt(expiresAt).build();
    }

    @Override
    public Map<String, SignedFileUrlDto> getResumePhotoUrls(List<Long> fileIds, Long ownerUserId) {
        if (fileIds == null || fileIds.size() > 100 || ownerUserId == null || ownerUserId <= 0
                || fileIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(CommonCode.INVALID_ARGUMENT);
        }
        if (fileIds.isEmpty()) return Map.of();
        var ids = new HashSet<>(fileIds);
        var files = fileMapper.selectList(Wrappers.<FileEntity>lambdaQuery()
                .in(FileEntity::getId, ids).eq(FileEntity::getOwnerUserId, ownerUserId)
                .eq(FileEntity::getTypeCode, UploadType.RESUME_PHOTO.getCode())
                .eq(FileEntity::getStatus, FileStatus.READY.getCode()));
        // 任一引用未通过核验时拒绝整批，不能把服务失败伪装成无照片。
        if (files.size() != ids.size()) throw new BusinessException(CommonCode.NOT_FOUND);
        var ttl = Duration.ofHours(2);
        var expiresAt = Instant.now().plus(ttl);
        Map<String, SignedFileUrlDto> urls = new HashMap<>();
        // GET 签名是 SDK 本地运算，不产生逐条数据库或对象存储查询。
        for (var file : files) urls.put(file.getId().toString(), SignedFileUrlDto.builder()
                .url(storage.getAccessUrl(file.getObjectKey(), ttl)).expiresAt(expiresAt).build());
        return urls;
    }

    /** 生成仅在内部 RPC 使用的审核结果，HTTP 响应另外映射为 VO。 */
    private ReadyFileDto toReadyFile(FileEntity file) {
        return ReadyFileDto.builder().fileId(file.getId().toString()).ownerUserId(file.getOwnerUserId())
                .typeCode(file.getTypeCode()).objectKey(file.getObjectKey()).size(file.getActualSize())
                .contentType(file.getContentType()).build();
    }
}
