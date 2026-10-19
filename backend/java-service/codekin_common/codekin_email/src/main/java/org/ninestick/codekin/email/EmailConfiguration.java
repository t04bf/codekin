package org.ninestick.codekin.email;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 邮件组件装配入口，业务服务显式导入。
 *
 * @author ninestick
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfiguration {
    /** 共用 Spring Mail 的 SMTP 连接配置。 */
    @Bean
    public EmailSender emailSender(JavaMailSender mailSender, EmailProperties properties) {
        return new NetEaseEmailSender(mailSender, properties);
    }
}
