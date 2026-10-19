package org.ninestick.codekin.user.service;

/**
 * 用户登录与手机号绑定的验证码业务接口。
 *
 * @author ninestick
 */
public interface IUserSmsCodeService {
    /** 发送可用于手机号登录的验证码。 */
    void sendLoginCode(String phoneNumber);

    /** 核验并一次性消费登录验证码。 */
    void verifyLoginCode(String phoneNumber, String code);

    /** 为当前登录用户发送新手机号绑定验证码。 */
    void sendBindCode(String phoneNumber);

    /** 核验并一次性消费当前用户的新手机号绑定码。 */
    void verifyBindCode(String phoneNumber, String code);
}
