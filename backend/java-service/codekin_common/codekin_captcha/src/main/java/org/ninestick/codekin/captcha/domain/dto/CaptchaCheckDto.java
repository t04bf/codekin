package org.ninestick.codekin.captcha.domain.dto;

import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ninestick.codekin.captcha.CaptchaConstants;

/**
 * 配套 Web SDK 提交的图片 ID 和拖动轨迹；成功与否由后端判断。
 *
 * @author ninestick
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaCheckDto {
    /** 生成接口签发的随机验证码 ID。 */
    @NotBlank
    @Size(max = CaptchaConstants.MAX_ID_LENGTH)
    private String id;
    /** 第三方 SDK 的轨迹协议对象。 */
    @NotNull
    private ImageCaptchaTrack data;
}
