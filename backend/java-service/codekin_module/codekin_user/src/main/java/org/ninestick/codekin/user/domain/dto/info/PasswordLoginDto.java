package org.ninestick.codekin.user.domain.dto.info;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 手机号或邮箱与密码登录的请求；不生成包含密码的 toString()。
 *
 * @author ninestick
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordLoginDto {

    /** 已验证的手机号或邮箱。 */
    @NotBlank
    private String identifier;

    /** 用户密码明文仅在本次请求中使用。 */
    @NotBlank
    private String password;
}
