package org.ninestick.codekin.user.domain.dto.info;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 邮箱登录或绑定验证码核验请求，不生成包含验证码的 toString()。
 *
 * @author ninestick
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerifyDto {
    /** 已收到验证码的邮箱。 */
    @NotBlank
    @Email
    @Size(max = 254)
    private String email;
    /** 六位数字验证码。 */
    @NotBlank
    @Pattern(regexp = "^\\d{6}$")
    private String code;
}
