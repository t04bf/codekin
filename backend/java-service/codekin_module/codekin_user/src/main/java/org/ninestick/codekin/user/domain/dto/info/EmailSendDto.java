package org.ninestick.codekin.user.domain.dto.info;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ninestick.codekin.captcha.CaptchaConstants;

/**
 * 邮箱登录或绑定验证码发送请求，用途由接口决定。
 *
 * @author ninestick
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailSendDto {
    /** 用户持有的邮箱地址。 */
    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    /** 登录发送必须携带滑块通过凭据；已登录的绑定接口暂不要求此字段。 */
    @Size(max = CaptchaConstants.MAX_ID_LENGTH)
    private String captchaId;
}
