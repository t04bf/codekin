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
 * 已发布模板版本的持久化数据，正文和布局不原地修改。
 *
 * @author ninestick
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("resume_template")
public class ResumeTemplateEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 稳定模板标识，例如 classic。 */
    private String templateKey;

    /** 模板版本；发布后渲染内容不可变。 */
    private Integer templateVersion;

    private String name;

    private String description;

    /** 受控模板封面资源路径，不存短期签名链接。 */
    private String coverPath;

    /** 支持的正文及布局协议版本。 */
    private Integer schemaVersion;

    /** 创建简历时复制的默认正文；保存原始 JSON 对象文本，业务层负责结构校验。 */
    private String defaultContent;

    /** 创建或切换模板时使用的默认布局；保存原始 JSON 对象文本，业务层负责结构校验。 */
    private String defaultLayout;

    /** DRAFT草稿 PUBLISHED可创建 RETIRED禁止新建但支持旧简历。 */
    private String status;

    /** 模板目录排序，越小越靠前。 */
    private Integer sortOrder;

    /** 创建时间 UTC。 */
    private LocalDateTime createdAt;

    /** 管理信息更新时间 UTC。 */
    private LocalDateTime updatedAt;
}

