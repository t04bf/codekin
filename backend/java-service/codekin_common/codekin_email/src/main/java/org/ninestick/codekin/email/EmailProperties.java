package org.ninestick.codekin.email;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 邮件发件人配置，授权码由 Spring Mail 的环境变量单独提供。
 *
 * @author ninestick
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "codekin.email")
public class EmailProperties {
    /** 已开通 SMTP 的网易个人邮箱地址。 */
    private String from;
}
