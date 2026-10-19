package org.ninestick.codekin.user.domain.dto.info;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 密码设置或修改请求；已有密码时必须提交旧密码。
 *
 * @author ninestick
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordSetDto {
    /** 当前密码；首次设置时可以省略。 */
    private String oldPassword;
    /** 新密码仅用于本次散列计算。 */
    @NotBlank
    @Size(min = 8, max = 72)
    private String newPassword;
}
