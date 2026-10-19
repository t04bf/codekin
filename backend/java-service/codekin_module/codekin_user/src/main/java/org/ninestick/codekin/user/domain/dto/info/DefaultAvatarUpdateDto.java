package org.ninestick.codekin.user.domain.dto.info;

import jakarta.validation.constraints.*;
import lombok.*;
import org.ninestick.codekin.model.file.constants.DefaultAvatarConstants;

/**
 * 设置预置头像的请求；路径还必须属于服务端配置白名单，不接受任意图片 URL。
 *
 * @author ninestick
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DefaultAvatarUpdateDto {
    /** 从默认头像列表取得的稳定对象路径。 */
    @NotBlank @Size(max = 512) @Pattern(regexp = DefaultAvatarConstants.OBJECT_KEY_PATTERN)
    private String objectKey;
}
