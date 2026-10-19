package org.ninestick.codekin.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 当前简历文档；更新必须包含归属、未删除状态及旧版本条件，并显式递增版本。
 *
 * @author ninestick
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_resume")
public class UserResumeEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属用户，来自鉴权上下文。 */
    private Long userId;

    /** 管理标题，例如 Java后端-校招，不要求唯一。 */
    private String title;

    /** 对应 resume_template.id，精确绑定模板版本。 */
    private Long templateId;

    /** 正文及布局协议版本。 */
    private Integer schemaVersion;

    /** 基本信息、经历、项目、自定义模块等正文；保存原始 JSON 对象文本，业务层负责结构校验。 */
    private String content;

    /** 字体、颜色、边距、模块排列和分栏配置；保存原始 JSON 对象文本，业务层负责结构校验。 */
    private String layout;

    /** 并发版本，每次保存、恢复或删除递增。 */
    private Long version;

    /** 最近一次成功修改的请求标识。 */
    private String lastMutationId;

    /** 最近修改请求的规范化SHA256，校验相同请求标识没有复用为不同载荷。 */
    private String lastMutationHash;

    /** 软删除时间；NULL为正常。 */
    private LocalDateTime deletedAt;

    /** 创建时间 UTC。 */
    private LocalDateTime createdAt;

    /** 最近修改时间 UTC。 */
    private LocalDateTime updatedAt;
}

