package org.ninestick.codekin.file.storage;

import static org.ninestick.codekin.file.storage.FileStorageConstants.*;
import io.minio.*;
import io.minio.Http.Method;
import io.minio.messages.CORSConfiguration;
import io.minio.messages.DeleteRequest;
import jakarta.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.ninestick.codekin.file.config.FileStorageProperties;
import org.ninestick.codekin.file.domain.enums.FileCode;
import org.ninestick.codekin.model.common.BusinessException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * S3 基础设施适配器，复用 SDK 的 POST Policy、对象访问和 CORS 配置。
 *
 * @author ninestick
 */
@Slf4j
@Component
public class S3Storage {
    /** 内部地址的读写客户端。 */
    @Resource private MinioClient storageClient;
    /** 浏览器地址的预签名客户端。 */
    @Resource private MinioClient publicStorageClient;
    /** 存储基础配置。 */
    @Resource private FileStorageProperties properties;
    /** 桶与跨域规则初始化成功后避免每次申请重新写入。 */
    private volatile boolean initialized;

    /** 存储未启动不阻止用户等模块启动；申请上传时重试初始化并返回明确错误。 */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeOnStartup() {
        try {
            ensureReady();
        } catch (BusinessException exception) {
            log.warn("对象存储尚未就绪，首次上传时将重试 code={}", exception.getRecode().getCode());
        }
    }

    /** 创建缺失桶并为本平台配置明确来源的跨域规则，多个文件服务实例使用相同配置。 */
    public synchronized void ensureReady() {
        if (initialized) return;
        try {
            if (!storageClient.bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build())) {
                try {
                    storageClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket())
                            .region(properties.getRegion()).build());
                } catch (io.minio.errors.ErrorResponseException exception) {
                    // 并发实例可能已经创建同名桶；重新检查存在性，不吞掉其他错误。
                    if (!storageClient.bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build())) {
                        throw exception;
                    }
                }
            }
            var rule = new CORSConfiguration.CORSRule(List.of(CONTENT_TYPE_FIELD), List.of("POST", "GET", "HEAD"),
                    properties.getAllowedOrigins(), List.of("ETag"), "codekin-web-upload", 600);
            storageClient.setBucketCors(SetBucketCorsArgs.builder().bucket(properties.getBucket())
                    .config(new CORSConfiguration(List.of(rule))).build());
            initialized = true;
            log.info("对象存储桶及跨域规则初始化成功");
        } catch (Exception exception) {
            log.error("对象存储初始化失败 exceptionType={}", exception.getClass().getSimpleName());
            throw new BusinessException(FileCode.STORAGE_UNAVAILABLE);
        }
    }

    /** 签名绑定唯一 key、声明 MIME 与大小范围；表单字段只含短期签名，不含 Secret Key。 */
    public Map<String, String> createPostFields(String objectKey, String contentType, long maxBytes, Instant expiresAt) {
        ensureReady();
        try {
            PostPolicy policy = new PostPolicy(properties.getBucket(), expiresAt.atZone(ZoneOffset.UTC));
            policy.addEqualsCondition("key", objectKey);
            policy.addEqualsCondition(CONTENT_TYPE_FIELD, contentType);
            policy.addEqualsCondition(SUCCESS_STATUS_FIELD, SUCCESS_STATUS);
            policy.addContentLengthRangeCondition(1L, maxBytes);
            Map<String, String> fields = new LinkedHashMap<>(publicStorageClient.getPresignedPostFormData(policy));
            fields.put("key", objectKey);
            fields.put("bucket", properties.getBucket());
            fields.put(CONTENT_TYPE_FIELD, contentType);
            fields.put(SUCCESS_STATUS_FIELD, SUCCESS_STATUS);
            return fields;
        } catch (Exception exception) {
            log.error("对象存储 POST 签名失败 exceptionType={}", exception.getClass().getSimpleName());
            throw new BusinessException(FileCode.STORAGE_UNAVAILABLE);
        }
    }

    /** 有界读取源文件；前后再次核对实际字节数，不能只相信 HEAD 或客户端声明。 */
    public byte[] readUpload(String objectKey, long maxBytes) {
        try {
            var stat = storageClient.statObject(StatObjectArgs.builder().bucket(properties.getBucket()).object(objectKey).build());
            if (stat.size() < 1 || stat.size() > maxBytes) throw new BusinessException(FileCode.SIZE_LIMIT);
            try (var input = storageClient.getObject(GetObjectArgs.builder().bucket(properties.getBucket()).object(objectKey).build())) {
                byte[] bytes = input.readNBytes(Math.toIntExact(maxBytes + 1));
                if (bytes.length < 1 || bytes.length > maxBytes) throw new BusinessException(FileCode.SIZE_LIMIT);
                return bytes;
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (io.minio.errors.ErrorResponseException exception) {
            if (Set.of("NoSuchKey", "NoSuchObject").contains(exception.errorResponse().code())) {
                throw new BusinessException(FileCode.NOT_READY);
            }
            log.error("对象存储读取失败 providerCode={}", exception.errorResponse().code());
            throw new BusinessException(FileCode.STORAGE_UNAVAILABLE);
        } catch (Exception exception) {
            log.error("对象存储读取失败 exceptionType={}", exception.getClass().getSimpleName());
            throw new BusinessException(FileCode.STORAGE_UNAVAILABLE);
        }
    }

    /** 正式文件由服务端写入，前端没有正式区的写入凭证。 */
    public void writePng(String objectKey, byte[] bytes) {
        try {
            storageClient.putObject(PutObjectArgs.builder().bucket(properties.getBucket()).object(objectKey)
                    .contentType(PNG_CONTENT_TYPE).stream(new ByteArrayInputStream(bytes), (long) bytes.length, -1L).build());
        } catch (Exception exception) {
            log.error("对象存储正式图片写入失败 exceptionType={}", exception.getClass().getSimpleName());
            throw new BusinessException(FileCode.STORAGE_UNAVAILABLE);
        }
    }

    /** 返回指定正式对象的短期 GET 地址，不能将存储桶改为公开读写。 */
    public String getAccessUrl(String objectKey) {
        return getAccessUrl(objectKey, properties.getAccessTtl());
    }

    /** 不同业务的读取期限独立指定，继续使用 SDK 生成 GET 签名。 */
    public String getAccessUrl(String objectKey, Duration ttl) {
        try {
            return publicStorageClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET).bucket(properties.getBucket()).object(objectKey)
                    .expiry(Math.toIntExact(ttl.toSeconds())).build());
        } catch (Exception exception) {
            log.error("对象存储读取签名失败 exceptionType={}", exception.getClass().getSimpleName());
            throw new BusinessException(FileCode.STORAGE_UNAVAILABLE);
        }
    }

    /** SDK 批量删除临时文件；检查惰性返回结果后，调用方才能标记清理完成。 */
    public void removeTemporaryObjects(List<String> objectKeys) {
        if (objectKeys.isEmpty()) return;
        try {
            var results = storageClient.removeObjects(RemoveObjectsArgs.builder().bucket(properties.getBucket())
                    .objects(objectKeys.stream().map(DeleteRequest.Object::new).toList()).build());
            boolean failed = false;
            for (var result : results) {
                var error = result.get();
                failed = true;
                log.error("对象存储临时文件清理失败 providerCode={}", error.code());
            }
            if (failed) throw new BusinessException(FileCode.STORAGE_UNAVAILABLE);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("对象存储临时文件清理异常 exceptionType={}", exception.getClass().getSimpleName());
            throw new BusinessException(FileCode.STORAGE_UNAVAILABLE);
        }
    }
}
