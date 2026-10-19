package org.ninestick.codekin.file.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.*;

/**
 * 文件服务管理的上传记录；用户模块只保存经核验的正式对象 key。
 *
 * @author ninestick
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("file_object")
public class FileEntity {
    /** 雪花文件 ID。 */
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    /** 归属用户 ID。 */
    private Long ownerUserId;
    /** UploadType 编码。 */
    private String typeCode;
    /** 临时上传对象 key，由后端生成。 */
    private String temporaryKey;
    /** 正式 key；只有 READY 状态允许被引用。 */
    private String objectKey;
    /** 源文件声明字节数，用于确认时核对实际上传。 */
    private Long declaredSize;
    /** 声明 MIME，必须与允许扩展名相符。 */
    private String declaredContentType;
    /** 正式文件实际字节数。 */
    private Long actualSize;
    /** 正式文件实际 MIME。 */
    private String contentType;
    /** FileStatus 编码。 */
    private String status;
    /** 确认期限，UTC。 */
    private LocalDateTime expiresAt;
    /** 创建时间，UTC。 */
    private LocalDateTime createdAt;
    /** 更新时间，UTC。 */
    private LocalDateTime updatedAt;
}

