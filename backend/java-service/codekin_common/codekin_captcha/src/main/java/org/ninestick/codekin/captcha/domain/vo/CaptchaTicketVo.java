package org.ninestick.codekin.captcha.domain.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * 验证成功后的短时一次性凭据，仅用于放行一次业务请求。
 *
 * @author ninestick
 */
@Getter
@Builder
public class CaptchaTicketVo {
    /** 复用组件二次验证 ID，不重新实现一套随机票据。 */
    private final String captchaId;
}
