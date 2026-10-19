package org.ninestick.codekin.resume;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.*;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.model.common.CommonCode;
import org.ninestick.codekin.dubbo.api.file.IFileApi;
import org.ninestick.codekin.resume.mapper.ResumeTemplateMapper;
import org.ninestick.codekin.resume.mapper.UserResumeMapper;
import org.ninestick.codekin.resume.mapper.UserResumeRevisionMapper;
import org.ninestick.codekin.resume.service.impl.ResumeServiceImpl;
import org.ninestick.codekin.security.UserContextHolder;
import org.ninestick.codekin.resume.domain.dto.ResumePdfDto;
import org.ninestick.codekin.resume.domain.enums.ResumeCode;
import org.ninestick.codekin.resume.domain.vo.ResumeDetailVo;
import org.ninestick.codekin.resume.service.IResumeService;
import org.ninestick.codekin.resume.service.impl.ResumePdfServiceImpl;
import org.ninestick.codekin.resume.render.ResumePdfRenderer;
import org.ninestick.codekin.utils.JsonUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.node.ObjectNode;

/** 导出沿用简历归属校验，并在启动浏览器前验证版本与图片载荷。 @author ninestick */
class ResumePdfServiceTest {
    private final IResumeService resumes = mock(IResumeService.class);
    private final ResumePdfRenderer renderer = mock(ResumePdfRenderer.class);
    private final ResumePdfServiceImpl service = new ResumePdfServiceImpl();
    private ResumeDetailVo detail;

    @BeforeEach void setup() throws Exception {
        ReflectionTestUtils.setField(service, "resumeService", resumes);
        ReflectionTestUtils.setField(service, "renderer", renderer);
        var document = JsonUtils.fromJson(new String(new ClassPathResource("resume/example.json").getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), ObjectNode.class);
        ((ObjectNode)document.path("layout").path("basic").path("photoConfig")).put("visible", false);
        detail = ResumeDetailVo.builder().id("42").title("后端简历").version("3").templateKey("classic").schemaVersion(1).templateVersion(1)
                .content(document.get("content")).layout(document.get("layout")).build();
        when(resumes.getResume(42L)).thenReturn(detail);
    }
    @Test void exportsOnlyConfirmedOwnedSnapshot() {
        byte[] pdf = "%PDF-1.7".getBytes();
        when(renderer.render(any())).thenReturn(pdf);
        assertArrayEquals(pdf, service.exportPdf(42L, ResumePdfDto.builder().expectedVersion("3").images(Map.of()).build()));
        var snapshot = org.mockito.ArgumentCaptor.forClass(ObjectNode.class);
        verify(renderer).render(snapshot.capture());
        assertEquals("3", snapshot.getValue().path("version").asString());
        assertEquals(detail.getContent(), snapshot.getValue().get("content"));
        verify(resumes).getResume(42L);
    }
    @Test void missingIdentityRejectsResumeAndPdfOperationsBeforeExternalAccess() {
        var ownedResumes = new ResumeServiceImpl();
        var resumeMapper = mock(UserResumeMapper.class);
        var templateMapper = mock(ResumeTemplateMapper.class);
        var revisionMapper = mock(UserResumeRevisionMapper.class);
        var files = mock(IFileApi.class);
        ReflectionTestUtils.setField(ownedResumes, "resumeMapper", resumeMapper);
        ReflectionTestUtils.setField(ownedResumes, "templateMapper", templateMapper);
        ReflectionTestUtils.setField(ownedResumes, "revisionMapper", revisionMapper);
        ReflectionTestUtils.setField(ownedResumes, "fileApi", files);
        ReflectionTestUtils.setField(service, "resumeService", ownedResumes);
        UserContextHolder.clear();
        List<Executable> operations = List.of(ownedResumes::getTemplates,
                () -> ownedResumes.getResumes(1, 20), () -> ownedResumes.getResume(42L),
                () -> ownedResumes.createResume(null), () -> ownedResumes.saveResume(42L, null),
                () -> ownedResumes.deleteResume(42L, null),
                () -> service.exportPdf(42L, ResumePdfDto.builder().expectedVersion("3").images(Map.of()).build()));
        for (var operation : operations) {
            assertEquals(CommonCode.UNAUTHORIZED, assertThrows(BusinessException.class, operation).getRecode());
        }
        verifyNoInteractions(resumeMapper, templateMapper, revisionMapper, files, renderer);
    }
    @Test void rejectsStaleVersionWithoutLaunchingBrowser() {
        assertEquals(ResumeCode.VERSION_CONFLICT, assertThrows(BusinessException.class, () -> service.exportPdf(42L,
                ResumePdfDto.builder().expectedVersion("2").images(Map.of()).build())).getRecode());
        verifyNoInteractions(renderer);
    }
    @Test void propagatesOwnershipFailureWithoutLaunchingBrowser() {
        when(resumes.getResume(42L)).thenThrow(new BusinessException(ResumeCode.NOT_FOUND));
        assertThrows(BusinessException.class, () -> service.exportPdf(42L, ResumePdfDto.builder().expectedVersion("3").images(Map.of()).build()));
        verifyNoInteractions(renderer);
    }
    @Test void refusesExtraImagesAndMissingOrNonRasterPhoto() {
        assertThrows(BusinessException.class, () -> service.exportPdf(42L, ResumePdfDto.builder().expectedVersion("3").images(Map.of("unknown", "data:image/png;base64,AA==")).build()));
        ((ObjectNode)detail.getLayout().path("basic").path("photoConfig")).put("visible", true);
        ((ObjectNode)detail.getContent().path("basic")).put("photo", "/test-photo.png");
        assertThrows(BusinessException.class, () -> service.exportPdf(42L, ResumePdfDto.builder().expectedVersion("3").images(Map.of()).build()));
        assertThrows(BusinessException.class, () -> service.exportPdf(42L, ResumePdfDto.builder().expectedVersion("3").images(Map.of("photo", "https://private.invalid/photo")).build()));
        verifyNoInteractions(renderer);
    }
}
