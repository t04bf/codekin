package org.ninestick.codekin.user.controller;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.ninestick.codekin.security.JwtSessionService;
import org.ninestick.codekin.security.UserContextHolder;
import org.ninestick.codekin.model.common.R;
import org.ninestick.codekin.user.domain.dto.info.PasswordLoginDto;
import org.ninestick.codekin.user.domain.dto.info.PasswordSetDto;
import org.ninestick.codekin.user.domain.dto.info.ProfileUpdateDto;
import org.ninestick.codekin.user.domain.dto.info.RefreshTokenDto;
import org.ninestick.codekin.user.domain.dto.info.EmailSendDto;
import org.ninestick.codekin.user.domain.dto.info.EmailVerifyDto;
import org.ninestick.codekin.user.domain.dto.info.SmsSendDto;
import org.ninestick.codekin.user.domain.dto.info.SmsVerifyDto;
import org.ninestick.codekin.user.domain.vo.info.LoginVo;
import org.ninestick.codekin.user.domain.vo.info.MyUserVo;
import org.ninestick.codekin.user.domain.vo.info.PublicUserVo;
import org.ninestick.codekin.user.service.IUserService;
import org.ninestick.codekin.user.service.IUserAvatarService;
import org.ninestick.codekin.user.domain.dto.info.AvatarUpdateDto;
import org.ninestick.codekin.user.domain.dto.info.DefaultAvatarUpdateDto;
import org.ninestick.codekin.user.domain.vo.info.DefaultAvatarVo;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理
 *
 * @author ninestick
 */
@RestController
@RequestMapping("/api")
public class UserController {

    /** 用户资料与登录业务。 */
    @Resource
    private IUserService userService;

    /** 通过内部文件核验安全关联用户头像。 */
    @Resource
    private IUserAvatarService userAvatarService;

    /** 注销当前已验签身份对应的 Redis 会话。 */
    @Resource
    private JwtSessionService jwtSessionService;

    /** 手机号或邮箱与密码登录。 */
    @PostMapping("/auth/password/login")
    public R<LoginVo> passwordLogin(@Valid @RequestBody PasswordLoginDto request) {
        return R.success(userService.passwordLogin(request, UserContextHolder.requireClientIp()));
    }

    /** 发送手机验证码登录所需的验证码。 */
    @PostMapping("/auth/sms/login/send")
    public R<Void> sendLoginCode(@Valid @RequestBody SmsSendDto request) {
        userService.sendLoginCode(request);
        return R.success(null);
    }

    /** 验证手机验证码并登录，首次使用时自动创建账号。 */
    @PostMapping("/auth/sms/login")
    public R<LoginVo> smsLogin(@Valid @RequestBody SmsVerifyDto request) {
        return R.success(userService.smsLogin(request, UserContextHolder.requireClientIp()));
    }

    /** 邮箱验证码由本服务生成，经个人网易邮箱 SMTP 投递。 */
    @PostMapping("/auth/email/login/send")
    public R<Void> sendEmailLoginCode(@Valid @RequestBody EmailSendDto request) {
        userService.sendEmailLoginCode(request);
        return R.success(null);
    }

    /** 核验邮箱验证码并登录，首次使用时自动注册。 */
    @PostMapping("/auth/email/login")
    public R<LoginVo> emailLogin(@Valid @RequestBody EmailVerifyDto request) {
        return R.success(userService.emailLogin(request, UserContextHolder.requireClientIp()));
    }

    /** 轮换一次性 Refresh Token，返回新的令牌对。 */
    @PostMapping("/auth/refresh")
    public R<LoginVo> refresh(@Valid @RequestBody RefreshTokenDto request) {
        JwtSessionService.IssuedToken issued = jwtSessionService.refresh(request.getRefreshToken());
        return R.success(LoginVo.builder().userId(issued.getUserId().toString())
                .accessToken(issued.getToken()).refreshToken(issued.getRefreshToken())
                .tokenType(org.ninestick.codekin.security.constants.AuthConstants.BEARER_TOKEN_TYPE)
                .expiresAt(issued.getExpiresAt()).refreshExpiresAt(issued.getRefreshExpiresAt())
                .sessionExpiresAt(issued.getSessionExpiresAt()).build());
    }

    /** 当前用户首次设置密码；已有密码时必须校验旧密码。 */
    @PutMapping("/users/me/password")
    public R<Void> setPassword(@Valid @RequestBody PasswordSetDto request) {
        userService.setPassword(request);
        return R.success(null);
    }

    /** 当前用户向新手机号发送绑定或换绑验证码。 */
    @PostMapping("/users/me/phone/sms/send")
    public R<Void> sendBindPhoneCode(@Valid @RequestBody SmsSendDto request) {
        userService.sendBindPhoneCode(request);
        return R.success(null);
    }

    /** 当前用户核验新号码后完成绑定或换绑。 */
    @PostMapping("/users/me/phone/bind")
    public R<Void> bindPhone(@Valid @RequestBody SmsVerifyDto request) {
        userService.bindPhone(request);
        return R.success(null);
    }

    /** 向新邮箱发送绑定或换绑验证码，必须已登录。 */
    @PostMapping("/users/me/email/code/send")
    public R<Void> sendBindEmailCode(@Valid @RequestBody EmailSendDto request) {
        userService.sendBindEmailCode(request);
        return R.success(null);
    }

    /** 验证新邮箱后绑定到当前账号，允许替换原邮箱。 */
    @PostMapping("/users/me/email/bind")
    public R<Void> bindEmail(@Valid @RequestBody EmailVerifyDto request) {
        userService.bindEmail(request);
        return R.success(null);
    }

    /** 撤销当前 JWT 对应的 Redis 会话。 */
    @PostMapping("/auth/logout")
    public R<Void> logout() {
        jwtSessionService.revokeSession(UserContextHolder.requireUser().getSessionId());
        return R.success(null);
    }

    /** 当前登录用户的完整基本资料。 */
    @GetMapping("/users/me")
    public R<MyUserVo> getMe() {
        return R.success(userService.getMe());
    }

    /** 编辑本人基础资料，身份来自网关验签上下文，不接受目标用户 ID。 */
    @PutMapping("/users/me/profile")
    public R<Void> updateProfile(@Valid @RequestBody ProfileUpdateDto request) {
        userService.updateProfile(request);
        return R.success(null);
    }

    /** 将已确认且属于当前用户的正式文件设置为头像。 */
    @PutMapping("/users/me/avatar")
    public R<Void> updateAvatar(@Valid @RequestBody AvatarUpdateDto request) {
        userAvatarService.updateAvatar(request);
        return R.success(null);
    }

    /** 获取 Nacos 中配置的默认头像及浏览器可访问的签名链接。 */
    @GetMapping("/users/avatar/defaults")
    public R<List<DefaultAvatarVo>> getDefaultAvatars() {
        return R.success(userAvatarService.getDefaultAvatars());
    }

    /** 选择默认资源；服务端校验配置白名单，身份由网关上下文确定。 */
    @PutMapping("/users/me/avatar/default")
    public R<Void> updateDefaultAvatar(@Valid @RequestBody DefaultAvatarUpdateDto request) {
        userAvatarService.updateDefaultAvatar(request);
        return R.success(null);
    }

    /** 按用户 ID 查看公开资料。 */
    @GetMapping("/users/{userId}")
    public R<PublicUserVo> getPublicProfile(@PathVariable Long userId) {
        return R.success(userService.getPublicProfile(userId));
    }
}
