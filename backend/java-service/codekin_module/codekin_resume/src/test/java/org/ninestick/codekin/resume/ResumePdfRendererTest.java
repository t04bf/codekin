package org.ninestick.codekin.resume;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.ninestick.codekin.resume.config.ResumePdfProperties;
import org.ninestick.codekin.resume.render.ResumePdfRenderer;
import org.ninestick.codekin.utils.JsonUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.node.ObjectNode;

/** 显式启用 Chromium 验证中文字体、A4 页面及分页，样例不含真实个人简历。 @author ninestick */
@EnabledIfSystemProperty(named = "resume.pdf.integration", matches = "true")
class ResumePdfRendererTest {
    private ResumePdfRenderer renderer() {
        var properties = new ResumePdfProperties();
        properties.setAssetsDirectory(System.getProperty("resume.pdf.assets", ""));
        properties.setExecutablePath(System.getProperty("resume.pdf.chromium", ""));
        var renderer = new ResumePdfRenderer();
        ReflectionTestUtils.setField(renderer, "properties", properties);
        return renderer;
    }
    private ObjectNode sample(String template) throws Exception {
        var document = JsonUtils.fromJson(new String(new ClassPathResource("resume/example.json").getInputStream().readAllBytes(), StandardCharsets.UTF_8), ObjectNode.class);
        document.put("id", "42").put("title", "后端开发验证").put("version", "1").put("templateKey", template).put("templateVersion", 1);
        ((ObjectNode)document.path("layout").path("basic").path("photoConfig")).put("visible", false);
        return document;
    }
    @Test void rendersEveryTemplateAsA4WithExtractableChineseAndCustomContent() throws Exception {
        Path output = Path.of("target/pdf-verification"); Files.createDirectories(output);
        for (String template : List.of("classic", "modern", "left-right", "timeline", "minimalist", "elegant", "creative", "editorial", "swiss")) {
            var snapshot = sample(template);
            byte[] pdf = renderer().render(snapshot);
            Files.write(output.resolve(template + ".pdf"), pdf);
            try (var parsed = Loader.loadPDF(pdf)) {
                assertTrue(parsed.getNumberOfPages() >= 1);
                for (var page : parsed.getPages()) {
                    assertEquals(595.28, page.getMediaBox().getWidth(), 1);
                    assertEquals(841.89, page.getMediaBox().getHeight(), 1);
                }
                String text = new PDFTextStripper().getText(parsed);
                assertTrue(text.contains("codekin"), template);
                assertTrue(text.contains("后端"), template);
                assertTrue(text.contains("并发"), template);
            }
        }
    }
    @Test void onePageScalingKeepsLastParagraphAndDisabledModeProducesMultiplePages() throws Exception {
        Path output = Path.of("target/pdf-verification"); Files.createDirectories(output);
        var snapshot = sample("classic");
        var sections = (tools.jackson.databind.node.ArrayNode)snapshot.path("layout").path("menuSections");
        sections.forEach(section -> ((ObjectNode)section).put("enabled", Set.of("basic", "skills").contains(section.path("id").asString())));
        var settings = (ObjectNode)snapshot.path("layout").path("globalSettings");
        settings.put("pagePadding", 12).put("baseFontSize", 16).put("lineHeight", 1.2).put("sectionSpacing", 0).put("paragraphSpacing", 1).put("sectionTitleSpacing", 0);
        var paragraphs = new StringBuilder();
        for (int i = 0; i < 54; i++) paragraphs.append("<p>后端开发分页验证第").append(i).append("段</p>");
        ((ObjectNode)snapshot.path("content")).put("skillContent", paragraphs.toString());
        settings.put("autoOnePage", false);
        try (var parsed = Loader.loadPDF(renderer().render(snapshot))) { assertTrue(parsed.getNumberOfPages() > 1); }
        settings.put("autoOnePage", true);
        byte[] fitted = renderer().render(snapshot);
        Files.write(output.resolve("one-page.pdf"), fitted);
        try (var parsed = Loader.loadPDF(fitted)) {
            assertEquals(1, parsed.getNumberOfPages());
            assertTrue(new PDFTextStripper().getText(parsed).contains("第53段"));
        }
    }
}
