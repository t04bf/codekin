package org.ninestick.codekin.user.service;

/**
 * 邮箱验证码业务接口。
 *
 * @author ninestick
 */
public interface IUserEmailCodeService {
    /** 发送登录验证码。 */
    void sendLoginCode(String email);
    /** 原子消费登录验证码。 */
    void verifyLoginCode(String email, String code);
    /** 为当前用户发送新邮箱绑定验证码，与登录用途隔离。 */
    void sendBindCode(String email);
    /** 原子消费当前用户的新邮箱绑定验证码。 */
    void verifyBindCode(String email, String code);
}
