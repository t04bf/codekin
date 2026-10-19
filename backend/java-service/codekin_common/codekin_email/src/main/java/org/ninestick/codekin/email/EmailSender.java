package org.ninestick.codekin.email;

import java.time.Duration;

/**
 * 邮件投递端口；验证码生成、限流与核验属于调用方业务。
 *
 * @author ninestick
 */
public interface EmailSender {
    /** 将业务层生成的验证码发送至指定邮箱。 */
    void sendCode(String address, String code, Duration validTime);
}
