package org.ninestick.codekin.resume.domain.dto;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.*;
import tools.jackson.databind.JsonNode;

/**
 * 整份替换简历业务 JSON；正文结构按仓库中的 JSON Schema 校验。
 * 用户身份、时间和新建 ID 由服务端决定，不接收客户端覆盖。
 * 显式使用无参 JSON 构造，缺少可选 checkpoint 时保留 false 默认值。
 * @author ninestick
 */
@Getter @Setter @Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class ResumeSaveDto {
    @NotBlank @Size(max = 120)
    private String title;
    /** 精确模板版本记录 ID，字符串避免浏览器整数精度损失。 */
    @NotNull @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    @DecimalMax("9223372036854775807")
    private String templateId;
    /** 前端组件数据结构版本，由共用协议校验。 */
    @NotNull @Min(1)
    private Integer schemaVersion;
    /** 简历正文 JSON 对象，包含基础资料、经历、项目和自定义内容；富文本字段使用受限 HTML 字符串。 */
    @NotNull
    private JsonNode content;
    /** 全局排版、字段顺序、图标与照片设置。 */
    @NotNull
    private JsonNode layout;
    /** 每次编辑产生新标识，更新重试必须复用原标识与载荷。 */
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$")
    private String mutationId;
    /** 新建不传；更新必须传读取到的版本。 */
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    @DecimalMax("9223372036854775807")
    private String expectedVersion;
    /** 显式保留历史版本；自动保存默认不生成永久快照。 */
    private boolean checkpoint;
}
