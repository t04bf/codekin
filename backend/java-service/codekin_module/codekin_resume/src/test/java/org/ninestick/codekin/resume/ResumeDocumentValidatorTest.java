package org.ninestick.codekin.resume;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.resume.document.ResumeDocumentValidator;
import org.ninestick.codekin.resume.domain.dto.ResumeSaveDto;
import org.ninestick.codekin.resume.domain.enums.ResumeCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 同一仓库文档的结构、HTML 与模块引用边界验证。 @author ninestick */
class ResumeDocumentValidatorTest {
    private ResumeDocumentValidator validator;
    private jakarta.validation.ValidatorFactory factory;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @BeforeEach void setup() throws Exception {
        validator = new ResumeDocumentValidator();
        factory = Validation.buildDefaultValidatorFactory();
        ReflectionTestUtils.setField(validator, "requestValidator", factory.getValidator());
        validator.initialize();
    }
    @AfterEach void close() { factory.close(); }

    static ResumeSaveDto request(String resource) throws Exception {
        try (var input = new ClassPathResource("resume/" + resource + ".json").getInputStream()) {
            var document = JSON.readTree(input);
            return ResumeSaveDto.builder().title("后端开发简历").templateId("910000000000000001")
                    .schemaVersion(1).content(document.get("content")).layout(document.get("layout"))
                    .mutationId("create-test").build();
        }
    }

    @Test void blankAndCustomDocumentValidateWithoutRewriting() throws Exception {
        for (String resource : new String[]{"blank", "example"}) {
            var request = request(resource);
            String before = JSON.writeValueAsString(request);
            assertEquals(64, validator.validateAndHash(request).length());
            assertEquals(before, JSON.writeValueAsString(request));
        }
    }
    @Test void hashIgnoresObjectKeyOrderButDetectsChangedPayload() throws Exception {
        var request = request("blank");
        String original = validator.validateAndHash(request);
        ((ObjectNode)request.getContent().get("basic")).remove("name");
        ((ObjectNode)request.getContent().get("basic")).put("name", "");
        assertEquals(original, validator.validateAndHash(request));
        request.setTitle("新标题");
        assertNotEquals(original, validator.validateAndHash(request));
    }
    @ParameterizedTest
    @ValueSource(strings = {"<script>alert(1)</script>", "<img src=x onerror=alert(1)>", "<p onclick=alert(1)>x</p>",
            "<a href='javascript:alert(1)'>x</a>", "<p style='background-image:url(https://a)'>x</p>",
            "<span style='position:fixed'>x</span>", "<iframe src='https://a'></iframe>"})
    void unsafeHtmlRejected(String html) throws Exception {
        var request = request("blank");
        ((ObjectNode)request.getContent()).put("skillContent", html);
        assertEquals(ResumeCode.INVALID_DOCUMENT, assertThrows(BusinessException.class,
                () -> validator.validateAndHash(request)).getRecode());
    }
    @Test void editorFormattingRemainsSupported() throws Exception {
        var request = request("blank");
        ((ObjectNode)request.getContent()).put("skillContent", "<p style='text-align: center'><strong>Java</strong> <span style='color: rgb(1, 2, 3)'>Spring</span></p><ol start='2' style='list-style-type: lower-alpha'><li>x</li></ol><p><mark data-color='#ff00ff' style='background-color: #ff00ff'>高亮</mark><a href='https://example.com' target='_blank' rel='noopener noreferrer'>项目</a></p>");
        assertDoesNotThrow(() -> validator.validateAndHash(request));
    }
    @Test void secretsUnknownFieldsAndOldShapeAreRejected() throws Exception {
        var request = request("blank");
        ((ObjectNode)request.getContent().get("basic")).put("githubKey", "secret");
        assertThrows(BusinessException.class, () -> validator.validateAndHash(request));
        ((ObjectNode)request.getContent().get("basic")).remove("githubKey");
        ((ObjectNode)request.getContent()).putArray("sections");
        assertThrows(BusinessException.class, () -> validator.validateAndHash(request));
    }
    @Test void customModuleMustBeDeclaredAndIdsMustBeUnique() throws Exception {
        var request = request("example");
        ((ObjectNode)request.getContent().get("customData")).set("missing", JSON.createArrayNode());
        assertThrows(BusinessException.class, () -> validator.validateAndHash(request));
        var blank = request("blank");
        var menu = (tools.jackson.databind.node.ArrayNode)blank.getLayout().get("menuSections");
        menu.add(menu.get(0).deepCopy());
        assertThrows(BusinessException.class, () -> validator.validateAndHash(blank));
    }
    @Test void photoReferenceCannotAlsoContainPreviewAndDocumentIsBounded() throws Exception {
        var request = request("blank");
        var basic = (ObjectNode)request.getContent().get("basic");
        basic.put("photoFileId", "99").put("photo", "data:image/png;base64,x");
        assertThrows(BusinessException.class, () -> validator.validateAndHash(request));
        basic.put("photo", "");
        assertDoesNotThrow(() -> validator.validateAndHash(request));
        basic.put("photoFileId", "9999999999999999999");
        assertThrows(BusinessException.class, () -> validator.validateAndHash(request));
        basic.put("photoFileId", "99");
        ((ObjectNode)request.getContent()).put("skillContent", "x".repeat(ResumeDocumentValidator.MAX_DOCUMENT_BYTES + 1));
        assertThrows(BusinessException.class, () -> validator.validateAndHash(request));
    }
}
