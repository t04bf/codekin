package org.ninestick.codekin.resume.domain.vo;

import lombok.Builder;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

/** 可用于创建简历的模板目录。 @author ninestick */
@Getter @Builder
public class ResumeTemplateVo {
    private final String id;
    /** React 渲染组件注册标识。 */
    private final String templateKey;
    private final Integer templateVersion;
    private final String name;
    private final String description;
    /** JSON 协议版本。 */
    private final Integer schemaVersion;
    /** 同一协议的默认正文。 */
    private final JsonNode defaultContent;
    /** 同一协议的默认排版。 */
    private final JsonNode defaultLayout;
}

