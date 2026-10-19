package org.ninestick.codekin.user.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.*;
import org.ninestick.codekin.model.file.constants.DefaultAvatarConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Nacos 维护的默认头像目录；只保存稳定对象路径，不保存会过期的签名 URL。
 *
 * @author ninestick
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "codekin.user.avatar")
public class UserAvatarProperties {
    /** 可选择及首次注册随机分配的头像，顺序与前端展示顺序一致。 */
    @NotEmpty @Size(max = DefaultAvatarConstants.MAX_BATCH_SIZE)
    private List<@NotNull @Valid DefaultAvatar> defaults = List.of();

    /** 避免重复路径导致随机分配偏差和前端重复选项。 */
    @AssertTrue(message = "默认头像对象路径不能重复")
    public boolean isUniqueObjectKeys() {
        return defaults != null && defaults.stream().filter(java.util.Objects::nonNull)
                .map(DefaultAvatar::getObjectKey).distinct().count() == defaults.size();
    }

    /** 单项配置的展示名称与对象路径，不允许由普通用户添加。 @author ninestick */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DefaultAvatar {
        /** 前端可访问的描述名称。 */
        @NotBlank @Size(max = 40) private String name;
        /** 存储桶内预置头像路径，大小受现有 avatar_key 字段限制。 */
        @NotBlank @Size(max = 512) @Pattern(regexp = DefaultAvatarConstants.OBJECT_KEY_PATTERN)
        private String objectKey;
    }
}
