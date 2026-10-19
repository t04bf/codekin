package org.ninestick.codekin.captcha;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 公共验证码接口的生成额度，实际值由 Nacos 配置。
 *
 * @author ninestick
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "codekin.captcha")
public class CaptchaProperties {
    /** 同一可信客户端 IP 在窗口内最多生成的图片数量。 */
    private long generateLimit = 30;

    /** 图片生成限额的固定窗口。 */
    private Duration generateWindow = Duration.ofMinutes(1);
}
