package org.ninestick.codekin.user.config;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * 配置绑定和默认资源命名空间边界；不依赖在线 Nacos。
 *
 * @author ninestick
 */
class UserAvatarPropertiesTest {
    @Test
    void bindsConfiguredCatalogAndRejectsMissingConfiguration() {
        var runner = new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);
        runner.withPropertyValues("codekin.user.avatar.defaults[0].name=眨眼",
                "codekin.user.avatar.defaults[0].object-key=uploads/avatar/default/avatar-01-wink.png")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals("uploads/avatar/default/avatar-01-wink.png",
                            context.getBean(UserAvatarProperties.class).getDefaults().getFirst().getObjectKey());
                });
        runner.run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void rejectsDuplicateKeysAndPathsOutsideDefaultDirectory() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var properties = new UserAvatarProperties();
            var valid = UserAvatarProperties.DefaultAvatar.builder().name("眨眼")
                    .objectKey("uploads/avatar/default/avatar-01-wink.png").build();
            properties.setDefaults(List.of(valid));
            assertTrue(validator.validate(properties).isEmpty());
            properties.setDefaults(List.of(valid, valid));
            assertFalse(validator.validate(properties).isEmpty());
            for (String key : List.of("https://storage.example/a.png", "uploads/avatar/default/../private.png",
                    "files/avatar/42/99.png")) {
                properties.setDefaults(List.of(UserAvatarProperties.DefaultAvatar.builder().name("错误").objectKey(key).build()));
                assertFalse(validator.validate(properties).isEmpty());
            }
        }
    }

    /** 启用真实配置绑定和校验，隔离业务基础设施。 */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(UserAvatarProperties.class)
    static class PropertiesConfiguration { }
}
