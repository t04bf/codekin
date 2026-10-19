-- 简历维护表结构草案，2026-10-04。面向项目当前 MySQL 8.4。
-- 仅供评审，未执行；不包含 AI、分享、协作和通用表单设计器。
-- 前两表为核心；历史版本表按对应功能启用。不设导出任务表。
-- BIGINT ID 由应用 ASSIGN_ID 生成，HTTP/JSON 以字符串传输。
-- 时间使用 UTC；跨用户服务/文件服务不加物理外键，应用负责归属与引用校验。

CREATE TABLE resume_template (
    id BIGINT NOT NULL COMMENT '模板版本记录 ID',
    template_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定模板标识，例如 classic',
    template_version INT NOT NULL COMMENT '模板版本；发布后渲染内容不可变',
    name VARCHAR(80) NOT NULL COMMENT '模板展示名称',
    description VARCHAR(500) NULL COMMENT '模板说明',
    cover_path VARCHAR(512) NULL COMMENT '受控模板封面资源路径，不存短期签名链接',
    schema_version INT NOT NULL COMMENT '支持的正文及布局协议版本',
    default_content JSON NOT NULL COMMENT '创建简历时复制的默认正文',
    default_layout JSON NOT NULL COMMENT '创建或切换模板时使用的默认布局',
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT草稿 PUBLISHED可创建 RETIRED禁止新建但支持旧简历',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '模板目录排序，越小越靠前',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '管理信息更新时间 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_template_key_version (template_key, template_version),
    KEY idx_template_catalog (status, sort_order, id),
    CONSTRAINT ck_template_versions CHECK (template_version > 0 AND schema_version > 0),
    CONSTRAINT ck_template_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_template_json CHECK (JSON_TYPE(default_content) = 'OBJECT' AND JSON_TYPE(default_layout) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简历模板版本目录';

CREATE TABLE user_resume (
    id BIGINT NOT NULL COMMENT '简历 ID',
    user_id BIGINT NOT NULL COMMENT '所属用户，来自鉴权上下文',
    title VARCHAR(120) NOT NULL COMMENT '管理标题，例如 Java后端-校招，不要求唯一',
    template_id BIGINT NOT NULL COMMENT '对应 resume_template.id，精确绑定模板版本',
    schema_version INT NOT NULL COMMENT '正文及布局协议版本',
    content JSON NOT NULL COMMENT '基本信息、经历、项目、自定义模块等正文',
    layout JSON NOT NULL COMMENT '字体、颜色、边距、模块排列和分栏配置',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '并发版本，每次保存、恢复或删除递增',
    last_mutation_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '最近一次成功修改的请求标识',
    last_mutation_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '最近修改请求的规范化SHA256，校验相同请求标识没有复用为不同载荷',
    deleted_at DATETIME(3) NULL COMMENT '软删除时间；NULL为正常',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近修改时间 UTC',
    PRIMARY KEY (id),
    KEY idx_resume_user_list (user_id, deleted_at, updated_at DESC, id DESC),
    KEY idx_resume_template (template_id),
    KEY idx_resume_deleted (deleted_at, id),
    CONSTRAINT ck_resume_versions CHECK (version > 0 AND schema_version > 0),
    CONSTRAINT ck_resume_json CHECK (JSON_TYPE(content) = 'OBJECT' AND JSON_TYPE(layout) = 'OBJECT'),
    CONSTRAINT ck_resume_mutation CHECK ((last_mutation_id IS NULL AND last_mutation_hash IS NULL) OR (last_mutation_id IS NOT NULL AND last_mutation_hash IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户私有简历当前状态';

-- 可选：提供持久化历史与恢复时启用；不等于编辑器每一步撤销。
CREATE TABLE user_resume_revision (
    id BIGINT NOT NULL COMMENT '快照 ID',
    resume_id BIGINT NOT NULL COMMENT '所属简历 ID',
    resume_version BIGINT NOT NULL COMMENT '快照对应的简历版本，允许不连续',
    title VARCHAR(120) NOT NULL COMMENT '该版本的管理标题',
    template_id BIGINT NOT NULL COMMENT '该版本绑定的模板版本记录 ID',
    schema_version INT NOT NULL COMMENT '该版本正文及布局协议版本',
    content JSON NOT NULL COMMENT '完整正文快照',
    layout JSON NOT NULL COMMENT '完整布局快照',
    reason VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'CREATE MANUAL CHECKPOINT BEFORE_RESTORE等服务端枚举',
    label VARCHAR(120) NULL COMMENT '用户为版本填写的备注',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '快照创建时间 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_revision_resume_version (resume_id, resume_version),
    KEY idx_revision_retention (created_at, id),
    KEY idx_revision_template (template_id),
    CONSTRAINT ck_revision_versions CHECK (resume_version > 0 AND schema_version > 0),
    CONSTRAINT ck_revision_json CHECK (JSON_TYPE(content) = 'OBJECT' AND JSON_TYPE(layout) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简历不可变历史快照';

-- 约定：
-- 1. 不增加公开状态或分享表，所有查询校验所属用户；历史通过所属简历校验权限。
-- 2. template_id已经定位版本，不再同时存template_key/template_version，避免两套来源。
-- 3. 所有模板版本在仍被当前简历或历史引用期间均不可物理删除。
-- 4. 历史快照也可能引用附件。不能按当前正文不再引用就删除文件。
--    本期不实现已确认附件的自动垃圾回收；若启用需补充跨业务引用登记与清理协议。
-- 5. 照片/证书/PDF复用file_object，但需增加私有用途和服务端生成文件的登记契约。
--    当前头像上传和公开头像签名接口不能直接复用为私有简历附件接口。
-- 6. last_mutation只识别最近修改。更旧重试返回版本冲突，不能声称支持无限期请求幂等。
-- 7. 恢复快照时新建当前版本，不倒退version。快照按显式版本点/节流留存。
-- 8. JSON CHECK只验证顶层类型；嵌套结构、数量、富文本、URL和样式范围由服务端schema校验。
-- 9. 前端通过 template_key + template_version 映射渲染组件，不再存 renderer_key。
-- 10. 不设导出任务表；PDF采用浏览器导出或同步接口另行确定，不隐含异步任务架构。
