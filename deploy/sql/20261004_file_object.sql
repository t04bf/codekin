-- 文件服务新增表；已有数据库可独立执行，本脚本不修改或清空用户数据。
CREATE TABLE IF NOT EXISTS file_object (
    id BIGINT NOT NULL COMMENT '雪花文件 ID',
    owner_user_id BIGINT NOT NULL COMMENT '所属 user_auth.id，由鉴权上下文确定',
    type_code VARCHAR(32) NOT NULL COMMENT 'UploadType 编码，例如 AVATAR',
    temporary_key VARCHAR(512) NULL COMMENT '后端生成的临时对象 key，清理后为空',
    object_key VARCHAR(512) NULL COMMENT '核验通过后生成的正式对象 key',
    declared_size BIGINT NOT NULL COMMENT '申请上传时声明的字节数',
    declared_content_type VARCHAR(100) NOT NULL COMMENT '签名锁定的 MIME',
    actual_size BIGINT NULL COMMENT '正式文件实际字节数',
    content_type VARCHAR(100) NULL COMMENT '正式文件实际 MIME',
    status VARCHAR(32) NOT NULL COMMENT 'UPLOADING PROCESSING READY REJECTED EXPIRED',
    expires_at DATETIME(3) NOT NULL COMMENT '确认截止时间 UTC',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间 UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_object_key (object_key),
    KEY idx_file_owner_type_status (owner_user_id, type_code, status),
    KEY idx_file_expire (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件上传与审核记录';
