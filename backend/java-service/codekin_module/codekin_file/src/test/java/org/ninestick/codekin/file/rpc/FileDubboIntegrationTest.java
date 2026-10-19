package org.ninestick.codekin.file.rpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.apache.dubbo.spring.boot.autoconfigure.*;
import org.junit.jupiter.api.Test;
import org.ninestick.codekin.dubbo.api.file.*;
import org.ninestick.codekin.file.service.IFileService;
import org.ninestick.codekin.file.domain.enums.FileCode;
import org.ninestick.codekin.model.common.*;
import org.ninestick.codekin.model.file.dto.ReadyFileDto;
import org.ninestick.codekin.model.file.enums.UploadType;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 验证当前 Spring Boot 的 Dubbo 自动装配、回环网络调用及 Fastjson2 传输，不依赖外部 Nacos 或数据库。
 *
 * @author ninestick
 */
class FileDubboIntegrationTest {
    @Test
    void starterExportsFileContractAndConsumerInvokesIt() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DubboAutoConfiguration.class,
                        DubboListenerAutoConfiguration.class, DubboRelaxedBinding2AutoConfiguration.class))
                .withUserConfiguration(RpcTestConfiguration.class)
                .withPropertyValues("dubbo.application.name=codekin-file-test", "dubbo.application.qos-enable=false",
                        "dubbo.registry.address=N/A", "dubbo.protocol.name=dubbo", "dubbo.protocol.host=127.0.0.1",
                        "dubbo.protocol.port=-1", "dubbo.protocol.serialization=fastjson2", "CODEKIN_DUBBO_FILE_TOKEN=test-only-token")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    IFileService service = context.getBean(IFileService.class);
                    when(service.getReadyFile(99L, 42L, "AVATAR")).thenReturn(ReadyFileDto.builder()
                            .fileId("99").ownerUserId(42L).typeCode("AVATAR").objectKey("files/avatar/42/99.png").build());
                    when(service.getReadyFile(103L, 42L, UploadType.RESUME_PHOTO.getCode())).thenReturn(ReadyFileDto.builder()
                            .fileId("103").ownerUserId(42L).typeCode(UploadType.RESUME_PHOTO.getCode())
                            .objectKey("files/resume_photo/42/103.png").build());
                    when(service.getAvatarUrls(java.util.List.of("files/avatar/42/99.png")))
                            .thenReturn(Map.of("files/avatar/42/99.png", "signed-url"));
                    when(service.getDefaultAvatarUrls(java.util.List.of("uploads/avatar/default/avatar-01-wink.png")))
                            .thenReturn(Map.of("uploads/avatar/default/avatar-01-wink.png", "default-signed-url"));
                    var reference = new ReferenceConfig<IFileApi>();
                    reference.setInterface(IFileApi.class);
                    reference.setGroup(FileRpcConstants.GROUP);
                    reference.setVersion(FileRpcConstants.VERSION);
                    reference.setInjvm(false);
                    var provider = context.getBeansOfType(org.apache.dubbo.config.spring.ServiceBean.class).values().stream()
                            .filter(bean -> IFileApi.class.getName().equals(bean.getInterface())).findFirst().orElseThrow();
                    var providerUrl = provider.getExportedUrls().stream().filter(url -> "dubbo".equals(url.getProtocol()))
                            .findFirst().orElseThrow();
                    reference.setUrl(providerUrl.toFullString());
                    reference.setScopeModel(context.getBean(org.apache.dubbo.config.spring.context.DubboSpringInitContext.class)
                            .getModuleModel());
                    try {
                        IFileApi api = reference.get();
                        var success = api.getReadyFile("99", 42L, "AVATAR");
                        assertTrue(success.isSuccess());
                        assertEquals(42L, success.getData().getOwnerUserId());
                        var portrait = api.getReadyFile("103", 42L, UploadType.RESUME_PHOTO.getCode());
                        assertTrue(portrait.isSuccess());
                        assertEquals(UploadType.RESUME_PHOTO.getCode(), portrait.getData().getTypeCode());
                        assertEquals("files/resume_photo/42/103.png", portrait.getData().getObjectKey());
                        assertEquals("signed-url", api.getAvatarUrls(java.util.List.of("files/avatar/42/99.png"))
                                .getData().get("files/avatar/42/99.png"));
                        var defaults = api.getDefaultAvatarUrls(java.util.List.of("uploads/avatar/default/avatar-01-wink.png"));
                        assertTrue(defaults.isSuccess());
                        assertEquals("default-signed-url", defaults.getData().get("uploads/avatar/default/avatar-01-wink.png"));
                        var invalid = api.getReadyFile("bad-id", 42L, "AVATAR");
                        assertEquals(CommonCode.INVALID_ARGUMENT.getCode(), invalid.getCode());
                        assertNotNull(invalid.getMessage());
                        assertEquals(CommonCode.NOT_FOUND.getCode(), api.getReadyFile("100", 42L, "AVATAR").getCode());

                        when(service.getReadyFile(101L, 42L, "AVATAR"))
                                .thenThrow(new BusinessException(FileCode.NOT_READY, "文件 {} 正在处理", 101));
                        var business = api.getReadyFile("101", 42L, "AVATAR");
                        assertEquals(FileCode.NOT_READY.getCode(), business.getCode());
                        assertEquals("文件 101 正在处理", business.getMessage());
                        assertNull(business.getData());

                        when(service.getReadyFile(102L, 42L, "AVATAR"))
                                .thenThrow(new IllegalStateException("private-secret-must-not-leak"));
                        var internal = api.getReadyFile("102", 42L, "AVATAR");
                        assertEquals(RpcCode.INTERNAL_ERROR.getCode(), internal.getCode());
                        assertTrue(internal.getMessage().contains("IllegalStateException"));
                        assertFalse(internal.getMessage().contains("private-secret"));

                        // 通过实际回环连接验证集群过滤器确实在网络超时之后返回统一结果。
                        when(service.getReadyFile(103L, 42L, "AVATAR")).thenAnswer(invocation -> {
                            Thread.sleep(700);
                            return ReadyFileDto.builder().fileId("103").ownerUserId(42L).typeCode("AVATAR").build();
                        });
                        var shortTimeout = new ReferenceConfig<IFileApi>();
                        shortTimeout.setInterface(IFileApi.class);
                        shortTimeout.setGroup(FileRpcConstants.GROUP);
                        shortTimeout.setVersion(FileRpcConstants.VERSION);
                        shortTimeout.setInjvm(false);
                        shortTimeout.setTimeout(100);
                        shortTimeout.setRetries(0);
                        shortTimeout.setUrl(providerUrl.addParameter("timeout", 100).addParameter("retries", 0).toFullString());
                        shortTimeout.setScopeModel(reference.getScopeModel());
                        try {
                            var timeout = shortTimeout.get().getReadyFile("103", 42L, "AVATAR");
                            assertEquals(RpcCode.CALL_FAILED.getCode(), timeout.getCode());
                            assertNotNull(timeout.getMessage());
                            assertNull(timeout.getData());
                        } finally {
                            shortTimeout.destroy();
                        }
                    } finally {
                        reference.destroy();
                    }
                });
    }

    /** 只扫描实际文件 RPC 提供方，业务实现通过模拟对象隔离外部中间件。 */
    @Configuration(proxyBeanMethods = false)
    @EnableDubbo(scanBasePackageClasses = FileApiImpl.class)
    static class RpcTestConfiguration {
        @Bean
        IFileService fileService() { return mock(IFileService.class); }
    }
}
