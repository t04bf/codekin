package org.ninestick.codekin.user.service;

import org.ninestick.codekin.user.domain.dto.info.PasswordLoginDto;
import org.ninestick.codekin.user.domain.dto.info.PasswordSetDto;
import org.ninestick.codekin.user.domain.dto.info.ProfileUpdateDto;
import org.ninestick.codekin.user.domain.dto.info.EmailSendDto;
import org.ninestick.codekin.user.domain.dto.info.EmailVerifyDto;
import org.ninestick.codekin.user.domain.dto.info.SmsSendDto;
import org.ninestick.codekin.user.domain.dto.info.SmsVerifyDto;
import org.ninestick.codekin.user.domain.vo.info.LoginVo;
import org.ninestick.codekin.user.domain.vo.info.MyUserVo;
import org.ninestick.codekin.user.domain.vo.info.PublicUserVo;

/**
 * 用户登录和基本资料的业务服务契约。
 *
 * @author ninestick
 */
public interface IUserService {

    /** 使用已验证的手机号或邮箱与密码登录。 */
    LoginVo passwordLogin(PasswordLoginDto request, String remoteAddress);

    /** 向手机号发送登录验证码，发送频率按客户端 IP 限制。 */
    void sendLoginCode(SmsSendDto request);

    /** 核验短信验证码并登录；未注册号码自动创建账号与默认资料。 */
    LoginVo smsLogin(SmsVerifyDto request, String remoteAddress);

    /** 发送邮箱登录验证码。 */
    void sendEmailLoginCode(EmailSendDto request);

    /** 核验邮箱验证码并登录；未注册邮箱自动创建账号。 */
    LoginVo emailLogin(EmailVerifyDto request, String remoteAddress);

    /** 首次设置密码，或验证旧密码后修改密码。 */
    void setPassword(PasswordSetDto request);

    /** 向新手机号发送绑定或换绑验证码。 */
    void sendBindPhoneCode(SmsSendDto request);

    /** 核验新号码并绑定到当前账号，允许替换原号码。 */
    void bindPhone(SmsVerifyDto request);

    /** 向新邮箱发送绑定或换绑验证码。 */
    void sendBindEmailCode(EmailSendDto request);

    /** 核验新邮箱并绑定到当前账号，允许替换原邮箱。 */
    void bindEmail(EmailVerifyDto request);

    /** 读取当前用户可见的完整基本资料。 */
    MyUserVo getMe();

    /** 替换当前用户可编辑资料，选填字段允许清空，不修改认证信息。 */
    void updateProfile(ProfileUpdateDto request);

    /** 读取指定用户允许公开的资料。 */
    PublicUserVo getPublicProfile(Long userId);
}
