package org.ninestick.codekin.resume.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.resume.domain.dto.ResumeSaveDto;
import org.ninestick.codekin.resume.domain.dto.ResumeDeleteDto;
import org.ninestick.codekin.resume.domain.enums.ResumeCode;
import org.ninestick.codekin.resume.domain.vo.*;
import org.ninestick.codekin.resume.service.IResumeService;
import org.ninestick.codekin.resume.service.IResumePdfService;
import org.ninestick.codekin.webmvc.GlobalExceptionHandler;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/** HTTP 返回真正的 JSON 对象与字符串 ID，并保留统一业务错误信封。 @author ninestick */
class ResumeControllerTest {
    private IResumeService service;
    private IResumePdfService pdfService;
    private MockMvc mvc;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @BeforeEach void setup() {
        service = mock(IResumeService.class);
        pdfService = mock(IResumePdfService.class);
        var controller = new ResumeController();
        ReflectionTestUtils.setField(controller, "resumeService", service);
        ReflectionTestUtils.setField(controller, "resumePdfService", pdfService);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void createAndUpdateBindDocumentWithoutLosingCustomFields() throws Exception {
        var document = JSON.readTree(new ClassPathResource("resume/example.json").getInputStream());
        var body = (tools.jackson.databind.node.ObjectNode)document.deepCopy();
        body.put("title", "后端开发").put("templateId", "910000000000000001").put("mutationId", "save-one");
        when(service.createResume(any())).thenReturn(ResumeDetailVo.builder().id("1234567890123456789")
                .version("1").schemaVersion(1).content(document.get("content")).layout(document.get("layout")).build());
        mvc.perform(post("/api/users/me/resumes").contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").value("1234567890123456789"))
                .andExpect(jsonPath("$.data.content").isMap()).andExpect(jsonPath("$.data.layout").isMap());
        var capture = ArgumentCaptor.forClass(ResumeSaveDto.class);
        verify(service).createResume(capture.capture());
        assertEquals(document.get("content"), capture.getValue().getContent());
        assertNull(capture.getValue().getExpectedVersion());
        assertFalse(capture.getValue().isCheckpoint());
        body.put("expectedVersion", "1").put("checkpoint", true);
        mvc.perform(put("/api/users/me/resumes/1234567890123456789").contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)))
                .andExpect(status().isOk());
        verify(service).saveResume(eq(1234567890123456789L), capture.capture());
        assertEquals("1", capture.getValue().getExpectedVersion());
        assertTrue(capture.getValue().isCheckpoint());
        assertEquals(document.get("layout"), capture.getValue().getLayout());
    }
    @Test void listDetailAndTemplateRoutesAreUnambiguous() throws Exception {
        when(service.getTemplates()).thenReturn(List.of());
        when(service.getResumes(1, 20)).thenReturn(ResumePageVo.builder().items(List.of()).page(1).pageSize(20).hasMore(false).build());
        when(service.getResume(42L)).thenThrow(new BusinessException(ResumeCode.NOT_FOUND));
        mvc.perform(get("/api/users/me/resumes/templates")).andExpect(jsonPath("$.code").value("0"));
        mvc.perform(get("/api/users/me/resumes")).andExpect(jsonPath("$.data.hasMore").value(false));
        mvc.perform(get("/api/users/me/resumes/42")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("RESUME_NOT_FOUND"));
        verify(service).getTemplates(); verify(service).getResumes(1, 20); verify(service).getResume(42L);
    }
    @Test void pdfReturnsPrivateBinaryAndErrorsRemainJson() throws Exception {
        byte[] pdf = "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(pdfService.exportPdf(eq(42L), any())).thenReturn(pdf);
        mvc.perform(post("/api/users/me/resumes/42/pdf").contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":\"3\",\"images\":{}}"))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(pdf)).andExpect(header().string("Cache-Control", "no-store"));
        when(pdfService.exportPdf(eq(42L), any())).thenThrow(new BusinessException(ResumeCode.VERSION_CONFLICT));
        mvc.perform(post("/api/users/me/resumes/42/pdf").contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":\"2\",\"images\":{}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("RESUME_VERSION_CONFLICT"));
    }
    @Test void malformedRequestFailsBeforePersistence() throws Exception {
        mvc.perform(post("/api/users/me/resumes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("COMMON_INVALID_ARGUMENT"));
        verifyNoInteractions(service);
    }
    @Test void listReturnsJsonPreviewAndPhotoExpiryWithoutExtraDetailRequests() throws Exception {
        var document = JSON.readTree(new ClassPathResource("resume/example.json").getInputStream());
        var expiry = java.time.Instant.parse("2026-10-11T12:00:00Z");
        var item = ResumeDetailVo.builder().id("1234567890123456789").templateKey("classic")
                .templateVersion(1).schemaVersion(1).version("1")
                .content(document.get("content")).layout(document.get("layout"))
                .photoUrl("https://signed.example/photo").photoUrlExpiresAt(expiry).build();
        when(service.getResumes(1, 20)).thenReturn(ResumePageVo.builder().items(List.of(item)).page(1).pageSize(20).hasMore(false).build());
        mvc.perform(get("/api/users/me/resumes"))
                .andExpect(jsonPath("$.data.items[0].id").value(item.getId()))
                .andExpect(jsonPath("$.data.items[0].content").isMap())
                .andExpect(jsonPath("$.data.items[0].layout").isMap())
                .andExpect(jsonPath("$.data.items[0].photoUrl").value(item.getPhotoUrl()))
                .andExpect(jsonPath("$.data.items[0].photoUrlExpiresAt").value(expiry.toString()));
        verify(service).getResumes(1, 20);
        verifyNoMoreInteractions(service);
    }
    @Test void deletionBindsAndValidatesVersionQueryParameter() throws Exception {
        mvc.perform(delete("/api/users/me/resumes/42").param("expectedVersion", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
        var capture = ArgumentCaptor.forClass(ResumeDeleteDto.class);
        verify(service).deleteResume(eq(42L), capture.capture());
        assertEquals("3", capture.getValue().getExpectedVersion());
        reset(service);
        mvc.perform(delete("/api/users/me/resumes/42"))
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_ARGUMENT"));
        mvc.perform(delete("/api/users/me/resumes/42").param("expectedVersion", "0"))
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_ARGUMENT"));
        verifyNoInteractions(service);
    }
}
