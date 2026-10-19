package org.ninestick.codekin.file.storage;

import static org.ninestick.codekin.file.storage.FileStorageConstants.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.ninestick.codekin.file.config.FileStorageProperties;
import org.ninestick.codekin.file.domain.entity.FileEntity;
import org.ninestick.codekin.file.domain.enums.FileStatus;
import org.ninestick.codekin.file.mapper.FileMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 批量清理过期临时上传，包括完成后被原凭证再次上传的临时文件；不删除正式头像。
 *
 * @author ninestick
 */
@Slf4j
@Component
public class TemporaryUploadCleanup {
    /** 文件记录。 */
    @Resource private FileMapper fileMapper;
    /** 存储批量删除。 */
    @Resource private S3Storage storage;
    /** 批大小与运行期限。 */
    @Resource private FileStorageProperties properties;

    /** 一个批次只执行一次列表查询及批量更新，不在记录循环中访问数据库。 */
    @Scheduled(fixedDelayString = "${codekin.file.storage.cleanup-interval:60000}", initialDelay = 60000)
    public void cleanup() {
        try {
            LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(CLEANUP_GRACE_MINUTES);
            List<FileEntity> files = fileMapper.selectList(Wrappers.<FileEntity>lambdaQuery()
                    .isNotNull(FileEntity::getTemporaryKey).lt(FileEntity::getExpiresAt, cutoff)
                    .orderByAsc(FileEntity::getExpiresAt).last("LIMIT " + properties.getCleanupBatchSize()));
            if (files.isEmpty()) return;
            storage.removeTemporaryObjects(files.stream().map(FileEntity::getTemporaryKey).toList());
            List<Long> ids = files.stream().map(FileEntity::getId).toList();
            fileMapper.update(Wrappers.<FileEntity>lambdaUpdate().in(FileEntity::getId, ids)
                    .ne(FileEntity::getStatus, FileStatus.READY.getCode())
                    .set(FileEntity::getStatus, FileStatus.EXPIRED.getCode()));
            fileMapper.update(Wrappers.<FileEntity>lambdaUpdate().in(FileEntity::getId, ids)
                    .set(FileEntity::getTemporaryKey, null).set(FileEntity::getUpdatedAt, LocalDateTime.now(ZoneOffset.UTC)));
            log.info("过期临时上传已清理 count={}", ids.size());
        } catch (RuntimeException exception) {
            log.error("过期临时上传清理失败 exceptionType={}", exception.getClass().getSimpleName());
        }
    }
}

