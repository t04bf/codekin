package org.ninestick.codekin.sms;

import java.time.Duration;

/**
 * 仅负责将业务层生成的验证码交给短信厂商发送。
 *
 * @author ninestick
 */
public interface SmsSender {
    /** 只提交业务生成的验证码；场景、配额与核验均由业务服务负责。 */
    void sendCode(String phoneNumber, String code, Duration validTime);
}
