package org.ninestick.codekin.file.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.ninestick.codekin.file.domain.dto.UploadSignatureDto;
import org.ninestick.codekin.file.domain.vo.UploadSignatureVo;
import org.ninestick.codekin.file.service.IFileService;
import org.ninestick.codekin.model.common.CommonCode;
import org.ninestick.codekin.model.file.dto.ReadyFileDto;
import org.ninestick.codekin.model.file.enums.UploadType;
import org.ninestick.codekin.security.AuthenticatedUser;
import org.ninestick.codekin.security.UserContextHolder;
import org.ninestick.codekin.webmvc.GlobalExceptionHandler;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 简历证件照复用统一上传 HTTP 契约；身份模拟已验签上下文，不连接真实存储或数据库。
 *
 * @author ninestick
 */
class FileControllerTest {
    /** 隔离文件业务，服务校验及真实 SDK 策略由对应测试覆盖。 */
    private IFileService service;
    /** 真实 MVC 请求绑定和 JSON 序列化。 */
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        UserContextHolder.clear();
        UserContextHolder.set(AuthenticatedUser.builder().userId(42L).sessionId("test-session").build());
        service = mock(IFileService.class);
        var controller = new FileController();
        ReflectionTestUtils.setField(controller, "fileService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
    }

    @Test
    void uploadTypesExposeResumePhotoLimitsAlongsideAvatar() throws Exception {
        mvc.perform(get("/api/files/upload-types"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data[?(@.code == 'RESUME_PHOTO')].maxBytes").value(hasItem(5242880)))
                .andExpect(jsonPath("$.data[?(@.code == 'RESUME_PHOTO')].maxWidth").value(hasItem(4096)))
                .andExpect(jsonPath("$.data[?(@.code == 'RESUME_PHOTO')].maxPixels").value(hasItem(12000000)))
                .andExpect(jsonPath("$.data[*].code").value(hasItem("AVATAR")));
    }

    @Test
    void signatureBindsResumePhotoAndReturnsExistingPostContract() throws Exception {
        when(service.createSignature(any(), eq(42L))).thenAnswer(invocation -> {
            UploadSignatureDto request = invocation.getArgument(0);
            assertEquals(UploadType.RESUME_PHOTO.getCode(), request.getTypeCode());
            assertEquals("portrait.png", request.getFileName());
            assertEquals(123456L, request.getSize());
            assertEquals("image/png", request.getContentType());
            return UploadSignatureVo.builder().fileId("9007199254740993")
                    .uploadUrl("https://storage.example/codekin").method("POST")
                    .fields(Map.of("key", "uploads/resume_photo/42/test.png", "policy", "test-policy"))
                    .expiresAt(Instant.now().plusSeconds(300))
                    .confirmationExpiresAt(Instant.now().plusSeconds(900)).build();
        });
        mvc.perform(post("/api/files/upload/signature").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"typeCode":"RESUME_PHOTO","fileName":"portrait.png","size":123456,"contentType":"image/png"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.fileId").value("9007199254740993"))
                .andExpect(jsonPath("$.data.method").value("POST"))
                .andExpect(jsonPath("$.data.uploadUrl").value("https://storage.example/codekin"))
                .andExpect(jsonPath("$.data.fields.key").value("uploads/resume_photo/42/test.png"))
                .andExpect(jsonPath("$.data.expiresAt").exists())
                .andExpect(jsonPath("$.data.confirmationExpiresAt").exists());
        verify(service).createSignature(any(), eq(42L));
    }

    @Test
    void completeReturnsResumeFileIdWithoutInternalKeyOrOwner() throws Exception {
        when(service.complete(99L, 42L)).thenReturn(ReadyFileDto.builder().fileId("99").ownerUserId(42L)
                .typeCode(UploadType.RESUME_PHOTO.getCode()).objectKey("files/resume_photo/42/99.png")
                .size(123L).contentType("image/png").build());
        mvc.perform(post("/api/files/99/complete"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.fileId").value("99"))
                .andExpect(jsonPath("$.data.typeCode").value("RESUME_PHOTO"))
                .andExpect(jsonPath("$.data.objectKey").doesNotExist())
                .andExpect(jsonPath("$.data.ownerUserId").doesNotExist());
    }

    @Test
    void anonymousRequestsCannotDiscoverApplyOrCompleteUploads() throws Exception {
        UserContextHolder.clear();
        mvc.perform(get("/api/files/upload-types"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(CommonCode.UNAUTHORIZED.getCode()));
        mvc.perform(post("/api/files/upload/signature").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"typeCode":"RESUME_PHOTO","fileName":"portrait.png","size":1,"contentType":"image/png"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(CommonCode.UNAUTHORIZED.getCode()));
        mvc.perform(post("/api/files/99/complete"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(CommonCode.UNAUTHORIZED.getCode()));
        verifyNoInteractions(service);
    }
}
