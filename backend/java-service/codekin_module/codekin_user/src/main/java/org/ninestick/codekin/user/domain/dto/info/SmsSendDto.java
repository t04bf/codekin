package org.ninestick.codekin.user.domain.dto.info;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ninestick.codekin.captcha.CaptchaConstants;

/**
 * 发送大陆手机号验证码的请求。
 *
 * @author ninestick
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsSendDto {
    /** 中国大陆 11 位手机号；持久化时添加 +86 区号。 */
    @NotBlank
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入有效的中国大陆手机号")
    private String phone;

    /** 登录发送必须携带滑块通过凭据；已登录的绑定接口暂不要求此字段。 */
    @Size(max = CaptchaConstants.MAX_ID_LENGTH)
    private String captchaId;
}
