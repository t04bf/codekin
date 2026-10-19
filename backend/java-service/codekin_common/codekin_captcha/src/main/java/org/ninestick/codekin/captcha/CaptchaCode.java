package org.ninestick.codekin.captcha;

import lombok.Getter;
import org.ninestick.codekin.model.common.Recode;

/**
 * 各业务服务共享的滑块验证错误码。
 *
 * @author ninestick
 */
@Getter
public enum CaptchaCode implements Recode {
    /** 生成接口的 IP 窗口额度已用完。 */
    TOO_FREQUENT("CAPTCHA_TOO_FREQUENT", "安全验证请求过于频繁，请稍后重试"),
    /** 未通过服务端轨迹校验，或验证码已过期。 */
    INVALID("CAPTCHA_INVALID", "安全验证未通过或已过期，请重新验证"),
    /** 发送接口未携带有效的一次性通过凭据。 */
    REQUIRED("CAPTCHA_REQUIRED", "请先完成安全验证；通过凭据只能使用一次"),
    /** 图片生成或验证码基础设施暂时不可用。 */
    UNAVAILABLE("CAPTCHA_UNAVAILABLE", "安全验证服务暂时不可用，请稍后重试");

    /** 稳定的业务状态码。 */
    private final String code;
    /** 默认提示。 */
    private final String message;

    CaptchaCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
