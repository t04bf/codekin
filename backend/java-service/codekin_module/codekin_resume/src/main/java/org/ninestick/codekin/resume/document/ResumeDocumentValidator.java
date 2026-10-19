package org.ninestick.codekin.resume.document;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.validation.Validator;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.resume.domain.dto.ResumeSaveDto;
import org.ninestick.codekin.resume.domain.enums.ResumeCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 仓库 JSON Schema 是正文结构的唯一来源；补充跨字段引用与受限 HTML 语义检查。
 * 不静默删字段、截断图片或清洗掉用户内容；不安全或不兼容的输入明确拒绝。
 * @author ninestick
 */
@Component
public class ResumeDocumentValidator {
    /** 当前已实现的文档协议，不等同于保存次数或渲染组件版本。 */
    public static final int SCHEMA_VERSION = 1;
    /** 整份正文与布局的 UTF-8 大小上限。 */
    public static final int MAX_DOCUMENT_BYTES = 1024 * 1024;
    /** 排序所有 Map 的键，使网络重试不受 JSON 对象属性顺序影响。 */
    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
    /** 只有当前编辑器生成的标签、属性和协议可以保存；不允许图片标签或事件属性。 */
    private static final Safelist HTML = new Safelist()
            .addTags("p", "br", "div", "span", "strong", "em", "b", "i", "u", "s", "del", "ul", "ol", "li",
                    "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "code", "a", "mark", "hr")
            .addAttributes(":all", "style", "class")
            .addAttributes("a", "href", "title", "target", "rel").addProtocols("a", "href", "http", "https", "mailto", "tel")
            .addAttributes("ol", "start").addAttributes("mark", "data-color");
    /** CSS 值只支持颜色、对齐与列表样式，不允许 URL、表达式或其他样式注入。 */
    private static final Pattern COLOR = Pattern.compile("(?i)(#[0-9a-f]{3,8}|[a-z]+|rgba?\\([0-9., %]+\\))");
    /** 固定标准模块 ID，不能用自定义内容覆盖其语义。 */
    private static final Set<String> STANDARD_SECTIONS = Set.of("basic", "skills", "experience", "projects", "education", "selfEvaluation", "certificates");
    /** Jakarta 参数约束，供 HTTP 与应用服务共同执行。 */
    @Resource private Validator requestValidator;
    /** 本地加载且仅包含本地引用的 schema，校验时不访问远程资源。 */
    private Schema schema;

    /** 启动时加载契约；缺少契约应明确阻止启动，不能降级为无校验保存。 */
    @PostConstruct
    public void initialize() throws Exception {
        try (var input = new ClassPathResource("resume/resume-document.schema.json").getInputStream()) {
            schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(input);
        }
    }

    /** 返回载荷指纹，用于相同 mutationId 的安全重试判断。 */
    public String validateAndHash(ResumeSaveDto request) {
        if (request == null || !requestValidator.validate(request).isEmpty()) throw new BusinessException(ResumeCode.INVALID_DOCUMENT);
        if (request.getSchemaVersion() != SCHEMA_VERSION) throw new BusinessException(ResumeCode.UNSUPPORTED_SCHEMA);
        var document = JSON.createObjectNode().put("schemaVersion", request.getSchemaVersion());
        document.set("content", request.getContent());
        document.set("layout", request.getLayout());
        if (JSON.writeValueAsBytes(document).length > MAX_DOCUMENT_BYTES || !schema.validate(document).isEmpty()) {
            throw new BusinessException(ResumeCode.INVALID_DOCUMENT);
        }
        JsonNode content = request.getContent(), layout = request.getLayout();
        Set<String> sections = new HashSet<>();
        for (var section : layout.path("menuSections")) {
            if (!sections.add(section.path("id").asString())) throw new BusinessException(ResumeCode.INVALID_DOCUMENT);
        }
        if (!sections.contains("basic")) throw new BusinessException(ResumeCode.INVALID_DOCUMENT);
        List<JsonNode> collections = new ArrayList<>(List.of(content.path("education"), content.path("experience"),
                content.path("projects"), content.path("certificates"), content.path("basic").path("customFields"),
                layout.path("basic").path("fieldOrder")));
        List<String> richTexts = new ArrayList<>(List.of(content.path("skillContent").asString(), content.path("selfEvaluationContent").asString()));
        for (var entry : content.path("customData").properties()) {
            if (!sections.contains(entry.getKey()) || STANDARD_SECTIONS.contains(entry.getKey())) throw new BusinessException(ResumeCode.INVALID_DOCUMENT);
            collections.add(entry.getValue());
            entry.getValue().forEach(item -> richTexts.add(item.path("description").asString()));
        }
        for (var collection : collections) {
            Set<String> ids = new HashSet<>();
            for (var item : collection) if (!ids.add(item.path("id").asString())) throw new BusinessException(ResumeCode.INVALID_DOCUMENT);
        }
        Set<String> fieldKeys = new HashSet<>();
        for (var field : layout.path("basic").path("fieldOrder")) {
            if (!fieldKeys.add(field.path("key").asString())) throw new BusinessException(ResumeCode.INVALID_DOCUMENT);
        }
        content.path("education").forEach(item -> richTexts.add(item.path("description").asString("")));
        content.path("experience").forEach(item -> richTexts.add(item.path("details").asString()));
        content.path("projects").forEach(item -> richTexts.add(item.path("description").asString()));
        for (var field : content.path("basic").path("customFields")) {
            if ("link".equals(field.path("valueType").asString()) && !field.path("value").asString().matches("(?i)^(|https?://[^\\s]+|mailto:[^\\s]+|tel:[^\\s]+)$")) {
                throw new BusinessException(ResumeCode.INVALID_DOCUMENT);
            }
        }
        if (!content.path("basic").path("photoFileId").isNull() && !content.path("basic").path("photo").asString().isEmpty()) {
            throw new BusinessException(ResumeCode.INVALID_DOCUMENT);
        }
        if (!content.path("basic").path("photoFileId").isNull()) {
            try { Long.parseLong(content.path("basic").path("photoFileId").asString()); }
            catch (NumberFormatException exception) { throw new BusinessException(ResumeCode.INVALID_DOCUMENT); }
        }
        for (String richText : richTexts) {
            if (!Jsoup.isValid(richText, HTML)) throw new BusinessException(ResumeCode.INVALID_DOCUMENT);
            var html = Jsoup.parseBodyFragment(richText);
            for (var element : html.select("[style]")) {
                for (String declaration : element.attr("style").split(";")) {
                    if (declaration.isBlank()) continue;
                    var parts = declaration.split(":", 2);
                    if (parts.length != 2) throw new BusinessException(ResumeCode.INVALID_DOCUMENT);
                    String property = parts[0].strip().toLowerCase(Locale.ROOT), value = parts[1].strip();
                    boolean valid = switch (property) {
                        case "color", "background-color" -> COLOR.matcher(value).matches();
                        case "text-align" -> Set.of("left", "center", "right", "justify").contains(value);
                        case "list-style-type" -> Set.of("disc", "circle", "square", "decimal", "lower-alpha", "upper-alpha", "lower-roman", "upper-roman", "none").contains(value);
                        default -> false;
                    };
                    if (!valid) throw new BusinessException(ResumeCode.INVALID_DOCUMENT);
                }
            }
        }
        try {
            Object sorted = JSON.readValue(JSON.writeValueAsString(request), Object.class);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(sorted)));
        } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
