package org.ninestick.codekin.email;

import java.time.Duration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

/**
 * 通过 Spring Mail 向网易个人邮箱 SMTP 服务器提交验证码邮件。
 *
 * @author ninestick
 */
public class NetEaseEmailSender implements EmailSender {
    /** 已按部署环境配置网易 SMTP 地址和授权码的发送器。 */
    private final JavaMailSender mailSender;
    /** 邮件头中的发件地址。 */
    private final EmailProperties properties;

    public NetEaseEmailSender(JavaMailSender mailSender, EmailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendCode(String address, String code, Duration validTime) {
        if (!StringUtils.hasText(properties.getFrom())) {
            throw new IllegalStateException("必须配置 codekin.email.from");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getFrom());
        message.setTo(address);
        message.setSubject("Codekin 登录验证码");
        message.setText("您的登录验证码是 " + code + "，" + validTime.toMinutes()
                + " 分钟内有效。若非本人操作，请忽略此邮件。");
        mailSender.send(message);
    }
}
