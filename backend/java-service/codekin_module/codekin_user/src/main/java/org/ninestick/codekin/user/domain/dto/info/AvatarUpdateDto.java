package org.ninestick.codekin.user.domain.dto.info;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

/**
 * 头像关联请求，只允许提交已确认文件 ID，不接受任意 URL 或对象 key。
 *
 * @author ninestick
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvatarUpdateDto {
    /** 文件服务创建的雪花 ID。 */
    @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") private String fileId;
}

