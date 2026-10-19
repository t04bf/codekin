package org.ninestick.codekin.resume.domain.vo;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

/** 本人简历完整内容，不暴露其他用户身份或存储对象路径。 @author ninestick */
@Getter @Builder
public class ResumeDetailVo {
    private final String id;
    private final String title;
    private final String templateId;
    /** 前端组件注册的模板标识。 */
    private final String templateKey;
    private final Integer templateVersion;
    /** JSON 协议版本。 */
    private final Integer schemaVersion;
    /** 内容 JSON 对象，不是二次编码的 JSON 字符串。 */
    private final JsonNode content;
    private final JsonNode layout;
    /** 列表或详情读取时签发的证件照临时访问链接，不存入正文。 */
    private final String photoUrl;
    /** 证件照链接到期时间 UTC。 */
    private final Instant photoUrlExpiresAt;
    /** 保存次数版本，后续更新时原样传回。 */
    private final String version;
    /** 创建时间 UTC。 */
    private final Instant createdAt;
    /** 更新时间 UTC。 */
    private final Instant updatedAt;
}
