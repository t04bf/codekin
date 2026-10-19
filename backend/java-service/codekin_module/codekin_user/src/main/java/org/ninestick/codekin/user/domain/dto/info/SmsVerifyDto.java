package org.ninestick.codekin.user.domain.dto.info;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 短信验证码登录或绑定手机号的请求；验证码不写入日志。
 *
 * @author ninestick
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsVerifyDto {
    /** 中国大陆 11 位手机号。 */
    @NotBlank
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入有效的中国大陆手机号")
    private String phone;

    /** 服务端生成的六位数字验证码。 */
    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "验证码应为六位数字")
    private String code;
}
