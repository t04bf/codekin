package org.ninestick.codekin.user.service.impl;

import static org.ninestick.codekin.sms.SmsConstants.CHINA_DIAL_CODE;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.ninestick.codekin.captcha.CaptchaService;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.security.JwtSessionService;
import org.ninestick.codekin.security.UserContextHolder;
import org.ninestick.codekin.security.constants.AuthConstants;
import org.ninestick.codekin.user.domain.dto.info.PasswordLoginDto;
import org.ninestick.codekin.user.domain.dto.info.PasswordSetDto;
import org.ninestick.codekin.user.domain.dto.info.ProfileUpdateDto;
import org.ninestick.codekin.user.domain.dto.info.EmailSendDto;
import org.ninestick.codekin.user.domain.dto.info.EmailVerifyDto;
import org.ninestick.codekin.user.domain.dto.info.SmsSendDto;
import org.ninestick.codekin.user.domain.dto.info.SmsVerifyDto;
import org.ninestick.codekin.user.domain.entity.info.UserAuthEntity;
import org.ninestick.codekin.user.domain.entity.info.UserProfileEntity;
import org.ninestick.codekin.user.domain.vo.info.LoginVo;
import org.ninestick.codekin.user.domain.vo.info.MyUserVo;
import org.ninestick.codekin.user.domain.vo.info.PublicUserVo;
import org.ninestick.codekin.user.domain.enums.UserCode;
import org.ninestick.codekin.user.domain.enums.UserAccountStatus;
import org.ninestick.codekin.user.mapper.UserAuthMapper;
import org.ninestick.codekin.user.mapper.UserProfileMapper;
import org.ninestick.codekin.user.service.IUserService;
import org.ninestick.codekin.user.service.IUserAvatarService;
import org.ninestick.codekin.user.service.IUserSmsCodeService;
import org.ninestick.codekin.user.service.IUserEmailCodeService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * 用户登录及资料维护。查询均按主键或唯一索引执行，不在循环中访问数据库。
 *
 * @author ninestick
 */
@Slf4j
@Service
public class UserServiceImpl implements IUserService {

    /** 获取已核验头像的短期访问地址。 */
    @Resource
    private IUserAvatarService userAvatarService;

    /** 公共滑块组件，匿名验证码发送前原子消费服务端通过凭据。 */
    @Resource
    private CaptchaService captchaService;

    /** 用户认证信息的持久化入口。 */
    @Resource
    private UserAuthMapper authMapper;

    /** 用户公开及私密资料的持久化入口。 */
    @Resource
    private UserProfileMapper profileMapper;

    /** 校验带算法标识的密码散列。 */
    @Resource
    private PasswordEncoder passwordEncoder;

    /** 登录成功后签发 JWT 并登记 Redis 会话。 */
    @Resource
    private JwtSessionService jwtSessionService;

    /** 用户业务自行限流和核验，短信模块仅负责投递。 */
    @Resource
    private IUserSmsCodeService userSmsCodeService;

    /** 邮箱验证码生成与核验属于用户业务。 */
    @Resource
    private IUserEmailCodeService userEmailCodeService;

    /** 自动注册时原子写入认证信息与默认资料。 */
    @Resource
    private TransactionTemplate transactionTemplate;

    /** 只允许已验证的标识登录；错误响应不区分账号不存在与密码错误。 */
    @Override
    public LoginVo passwordLogin(PasswordLoginDto request, String remoteAddress) {
        String identifier = request.getIdentifier().strip();
        boolean emailLogin = identifier.contains("@");
        if (emailLogin) {
            identifier = identifier.toLowerCase(Locale.ROOT);
        } else if (identifier.matches("^1[3-9]\\d{9}$")) {
            // 表中保存 +86 前缀，密码登录同时接受常用的 11 位手机号输入。
            identifier = CHINA_DIAL_CODE + identifier;
        }
        UserAuthEntity auth = authMapper.selectOne(Wrappers.<UserAuthEntity>lambdaQuery()
                .eq(emailLogin, UserAuthEntity::getEmail, identifier)
                .eq(!emailLogin, UserAuthEntity::getPhone, identifier));
        if (auth == null || !UserAccountStatus.NORMAL.getCode().equals(auth.getStatus())
                || (emailLogin ? auth.getEmailVerifiedAt() == null : auth.getPhoneVerifiedAt() == null)
                || auth.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), auth.getPasswordHash())) {
            log.warn("密码登录失败");
            throw new BusinessException(UserCode.INVALID_CREDENTIALS);
        }

        // 只更新最近一次成功登录字段；IP 由网关提取并签名，业务拦截器写入上下文。
        LocalDateTime loginAt = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        int updated = authMapper.update(null, Wrappers.<UserAuthEntity>lambdaUpdate()
                .eq(UserAuthEntity::getId, auth.getId())
                .eq(UserAuthEntity::getStatus, UserAccountStatus.NORMAL.getCode())
                .set(UserAuthEntity::getLastLoginIp, remoteAddress)
                .set(UserAuthEntity::getLastLoginAt, loginAt)
                .set(UserAuthEntity::getUpdatedAt, loginAt));
        if (updated != 1) {
            // 密码已验证；更新未成功可能是账号状态在查询后变化，不能归因于密码错误。
            log.warn("登录状态更新未成功 userId={} affectedRows={}", auth.getId(), updated);
            throw new BusinessException(UserCode.LOGIN_NOT_COMPLETED);
        }
        JwtSessionService.IssuedToken issued = jwtSessionService.issue(auth.getId());
        log.info("用户密码登录成功 userId={}", auth.getId());
        return LoginVo.builder()
                .userId(auth.getId().toString())
                .accessToken(issued.getToken())
                .refreshToken(issued.getRefreshToken())
                .tokenType(AuthConstants.BEARER_TOKEN_TYPE)
                .expiresAt(issued.getExpiresAt())
                .refreshExpiresAt(issued.getRefreshExpiresAt())
                .sessionExpiresAt(issued.getSessionExpiresAt())
                .build();
    }

    /** 登录验证码发送不区分手机号是否已经注册，避免账号枚举。 */
    @Override
    public void sendLoginCode(SmsSendDto request) {
        captchaService.consume(request.getCaptchaId());
        userSmsCodeService.sendLoginCode(request.getPhone());
    }

    /** 已验证的手机号登录；不存在时在单个数据库事务内创建认证与资料记录。 */
    @Override
    public LoginVo smsLogin(SmsVerifyDto request, String remoteAddress) {
        String storedPhone = CHINA_DIAL_CODE + request.getPhone();
        userSmsCodeService.verifyLoginCode(request.getPhone(), request.getCode());

        LocalDateTime loginAt = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        UserAuthEntity auth = authMapper.selectOne(Wrappers.<UserAuthEntity>lambdaQuery()
                .eq(UserAuthEntity::getPhone, storedPhone));
        boolean created = false;
        if (auth == null) {
            UserAuthEntity newAuth = UserAuthEntity.builder()
                    .status(UserAccountStatus.NORMAL.getCode())
                    .phone(storedPhone)
                    .phoneVerifiedAt(loginAt)
                    .lastLoginIp(remoteAddress)
                    .lastLoginAt(loginAt)
                    .createdAt(loginAt)
                    .updatedAt(loginAt)
                    .build();
            try {
                auth = transactionTemplate.execute(status -> {
                    if (authMapper.insert(newAuth) != 1) {
                        throw new BusinessException(UserCode.LOGIN_NOT_COMPLETED);
                    }
                    UserProfileEntity profile = UserProfileEntity.builder()
                            .userId(newAuth.getId())
                            .nickname("用户" + newAuth.getId())
                            .avatarKey(userAvatarService.pickRandomDefaultAvatarKey())
                            .createdAt(loginAt)
                            .updatedAt(loginAt)
                            .build();
                    if (profileMapper.insert(profile) != 1) {
                        throw new BusinessException(UserCode.LOGIN_NOT_COMPLETED);
                    }
                    return newAuth;
                });
                created = true;
            } catch (DuplicateKeyException exception) {
                // 同一号码并发注册时，唯一索引只允许一个账号；复用已提交的账号。
                auth = authMapper.selectOne(Wrappers.<UserAuthEntity>lambdaQuery()
                        .eq(UserAuthEntity::getPhone, storedPhone));
                if (auth == null) {
                    throw new BusinessException(UserCode.LOGIN_NOT_COMPLETED);
                }
            }
        }
        if (!UserAccountStatus.NORMAL.getCode().equals(auth.getStatus())) {
            throw new BusinessException(UserCode.ACCOUNT_UNAVAILABLE);
        }
        // 未验证的旧记录可能属于其他人，不能因持有该手机号就接管其整个账号。
        if (auth.getPhoneVerifiedAt() == null) {
            throw new BusinessException(UserCode.PHONE_UNVERIFIED);
        }
        if (!created) {
            int updated = authMapper.update(null, Wrappers.<UserAuthEntity>lambdaUpdate()
                    .eq(UserAuthEntity::getId, auth.getId())
                    .eq(UserAuthEntity::getStatus, UserAccountStatus.NORMAL.getCode())
                    .eq(UserAuthEntity::getPhone, storedPhone)
                    .isNotNull(UserAuthEntity::getPhoneVerifiedAt)
                    .set(UserAuthEntity::getLastLoginIp, remoteAddress)
                    .set(UserAuthEntity::getLastLoginAt, loginAt)
                    .set(UserAuthEntity::getUpdatedAt, loginAt));
            if (updated != 1) {
                log.warn("短信登录状态更新未成功 userId={} affectedRows={}", auth.getId(), updated);
                throw new BusinessException(UserCode.LOGIN_NOT_COMPLETED);
            }
        }
        JwtSessionService.IssuedToken issued = jwtSessionService.issue(auth.getId());
        log.info("用户短信登录成功 userId={} created={}", auth.getId(), created);
        return LoginVo.builder()
                .userId(auth.getId().toString())
                .accessToken(issued.getToken())
                .refreshToken(issued.getRefreshToken())
                .tokenType(AuthConstants.BEARER_TOKEN_TYPE)
                .expiresAt(issued.getExpiresAt())
                .refreshExpiresAt(issued.getRefreshExpiresAt())
                .sessionExpiresAt(issued.getSessionExpiresAt())
                .build();
    }

    /** 邮箱验证码发送不区分是否注册，降低账号枚举风险。 */
    @Override
    public void sendEmailLoginCode(EmailSendDto request) {
        captchaService.consume(request.getCaptchaId());
        userEmailCodeService.sendLoginCode(request.getEmail());
    }

    /** 已验证的邮箱登录；首次使用时在同一事务写入认证与默认资料。 */
    @Override
    public LoginVo emailLogin(EmailVerifyDto request, String remoteAddress) {
        String email = request.getEmail().strip().toLowerCase(Locale.ROOT);
        userEmailCodeService.verifyLoginCode(email, request.getCode());
        LocalDateTime loginAt = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        UserAuthEntity auth = authMapper.selectOne(Wrappers.<UserAuthEntity>lambdaQuery()
                .eq(UserAuthEntity::getEmail, email));
        boolean created = false;
        if (auth == null) {
            UserAuthEntity newAuth = UserAuthEntity.builder()
                    .status(UserAccountStatus.NORMAL.getCode())
                    .email(email).emailVerifiedAt(loginAt)
                    .lastLoginIp(remoteAddress).lastLoginAt(loginAt)
                    .createdAt(loginAt).updatedAt(loginAt).build();
            try {
                auth = transactionTemplate.execute(status -> {
                    if (authMapper.insert(newAuth) != 1) {
                        throw new BusinessException(UserCode.LOGIN_NOT_COMPLETED);
                    }
                    UserProfileEntity profile = UserProfileEntity.builder()
                            .userId(newAuth.getId()).nickname("用户" + newAuth.getId())
                            .avatarKey(userAvatarService.pickRandomDefaultAvatarKey())
                            .createdAt(loginAt).updatedAt(loginAt).build();
                    if (profileMapper.insert(profile) != 1) {
                        throw new BusinessException(UserCode.LOGIN_NOT_COMPLETED);
                    }
                    return newAuth;
                });
                created = true;
            } catch (DuplicateKeyException exception) {
                auth = authMapper.selectOne(Wrappers.<UserAuthEntity>lambdaQuery()
                        .eq(UserAuthEntity::getEmail, email));
                if (auth == null) {
                    throw new BusinessException(UserCode.LOGIN_NOT_COMPLETED);
                }
            }
        }
        if (!UserAccountStatus.NORMAL.getCode().equals(auth.getStatus())) {
            throw new BusinessException(UserCode.ACCOUNT_UNAVAILABLE);
        }
        // 未验证的旧邮箱字段不因验证码成功而自动接管已有账号。
        if (auth.getEmailVerifiedAt() == null) {
            throw new BusinessException(UserCode.EMAIL_UNVERIFIED);
        }
        if (!created) {
            int updated = authMapper.update(null, Wrappers.<UserAuthEntity>lambdaUpdate()
                    .eq(UserAuthEntity::getId, auth.getId())
                    .eq(UserAuthEntity::getStatus, UserAccountStatus.NORMAL.getCode())
                    .eq(UserAuthEntity::getEmail, email)
                    .isNotNull(UserAuthEntity::getEmailVerifiedAt)
                    .set(UserAuthEntity::getLastLoginIp, remoteAddress)
                    .set(UserAuthEntity::getLastLoginAt, loginAt)
                    .set(UserAuthEntity::getUpdatedAt, loginAt));
            if (updated != 1) {
                log.warn("邮箱登录状态更新未成功 userId={} affectedRows={}", auth.getId(), updated);
                throw new BusinessException(UserCode.LOGIN_NOT_COMPLETED);
            }
        }
        JwtSessionService.IssuedToken issued = jwtSessionService.issue(auth.getId());
        log.info("用户邮箱登录成功 userId={} created={}", auth.getId(), created);
        return LoginVo.builder().userId(auth.getId().toString())
                .accessToken(issued.getToken()).refreshToken(issued.getRefreshToken())
                .tokenType(AuthConstants.BEARER_TOKEN_TYPE).expiresAt(issued.getExpiresAt())
                .refreshExpiresAt(issued.getRefreshExpiresAt())
                .sessionExpiresAt(issued.getSessionExpiresAt()).build();
    }

    /** 首次设置无需旧密码；已有密码必须先验证，再撤销该用户所有会话。 */
    @Override
    public void setPassword(PasswordSetDto request) {
        Long userId = UserContextHolder.requireUserId();
        UserAuthEntity auth = authMapper.selectById(userId);
        if (auth == null || !UserAccountStatus.NORMAL.getCode().equals(auth.getStatus())) {
            throw new BusinessException(UserCode.ACCOUNT_UNAVAILABLE);
        }
        if (auth.getPasswordHash() != null) {
            if (request.getOldPassword() == null || request.getOldPassword().isBlank()) {
                throw new BusinessException(UserCode.PASSWORD_OLD_REQUIRED);
            }
            if (!passwordEncoder.matches(request.getOldPassword(), auth.getPasswordHash())) {
                throw new BusinessException(UserCode.PASSWORD_OLD_INVALID);
            }
        }
        String encoded = passwordEncoder.encode(request.getNewPassword());
        LocalDateTime updatedAt = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        var update = Wrappers.<UserAuthEntity>lambdaUpdate()
                .eq(UserAuthEntity::getId, userId)
                .eq(UserAuthEntity::getStatus, UserAccountStatus.NORMAL.getCode())
                .set(UserAuthEntity::getPasswordHash, encoded)
                .set(UserAuthEntity::getPasswordUpdatedAt, updatedAt)
                .set(UserAuthEntity::getUpdatedAt, updatedAt);
        if (auth.getPasswordHash() == null) {
            update.isNull(UserAuthEntity::getPasswordHash);
        } else {
            update.eq(UserAuthEntity::getPasswordHash, auth.getPasswordHash());
        }
        // 更新前后都提高会话代数，阻止并发旧密码登录在更新期间取得可用会话。
        jwtSessionService.revokeAll(userId);
        int updated = authMapper.update(null, update);
        jwtSessionService.revokeAll(userId);
        if (updated != 1) {
            throw new BusinessException(UserCode.PASSWORD_UPDATE_NOT_COMPLETED);
        }
        log.info("用户密码已设置或修改，原有会话全部失效 userId={}", userId);
    }

    /** 换绑只验证新号码；要求调用方持有有效的 JWT 与 Redis 会话。 */
    @Override
    public void sendBindPhoneCode(SmsSendDto request) {
        Long userId = UserContextHolder.requireUserId();
        String storedPhone = CHINA_DIAL_CODE + request.getPhone();
        UserAuthEntity auth = authMapper.selectById(userId);
        if (auth == null || !UserAccountStatus.NORMAL.getCode().equals(auth.getStatus())) {
            throw new BusinessException(UserCode.ACCOUNT_UNAVAILABLE);
        }
        if (storedPhone.equals(auth.getPhone())) {
            throw new BusinessException(UserCode.PHONE_UNCHANGED);
        }
        UserAuthEntity existing = authMapper.selectOne(Wrappers.<UserAuthEntity>lambdaQuery()
                .eq(UserAuthEntity::getPhone, storedPhone));
        if (existing != null) {
            throw new BusinessException(UserCode.PHONE_ALREADY_BOUND);
        }
        userSmsCodeService.sendBindCode(request.getPhone());
    }

    /** 用新号码验证码完成绑定或换绑，并依赖唯一索引阻止并发重复绑定。 */
    @Override
    public void bindPhone(SmsVerifyDto request) {
        Long userId = UserContextHolder.requireUserId();
        String storedPhone = CHINA_DIAL_CODE + request.getPhone();
        UserAuthEntity auth = authMapper.selectById(userId);
        if (auth == null || !UserAccountStatus.NORMAL.getCode().equals(auth.getStatus())) {
            throw new BusinessException(UserCode.ACCOUNT_UNAVAILABLE);
        }
        if (storedPhone.equals(auth.getPhone())) {
            throw new BusinessException(UserCode.PHONE_UNCHANGED);
        }
        userSmsCodeService.verifyBindCode(request.getPhone(), request.getCode());
        LocalDateTime verifiedAt = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        var update = Wrappers.<UserAuthEntity>lambdaUpdate()
                .eq(UserAuthEntity::getId, userId)
                .eq(UserAuthEntity::getStatus, UserAccountStatus.NORMAL.getCode())
                .set(UserAuthEntity::getPhone, storedPhone)
                .set(UserAuthEntity::getPhoneVerifiedAt, verifiedAt)
                .set(UserAuthEntity::getUpdatedAt, verifiedAt);
        if (auth.getPhone() == null) {
            update.isNull(UserAuthEntity::getPhone);
        } else {
            update.eq(UserAuthEntity::getPhone, auth.getPhone());
        }
        try {
            int updated = authMapper.update(null, update);
            if (updated != 1) {
                throw new BusinessException(UserCode.PHONE_BIND_NOT_COMPLETED);
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(UserCode.PHONE_ALREADY_BOUND);
        }
        log.info("用户手机号绑定或换绑成功 userId={}", userId);
    }

    /** 当前有效会话加新邮箱验证，沿用手机号绑定规则。 */
    @Override
    public void sendBindEmailCode(EmailSendDto request) {
        Long userId = UserContextHolder.requireUserId();
        String email = request.getEmail().strip().toLowerCase(Locale.ROOT);
        UserAuthEntity auth = authMapper.selectById(userId);
        if (auth == null || !UserAccountStatus.NORMAL.getCode().equals(auth.getStatus())) {
            throw new BusinessException(UserCode.ACCOUNT_UNAVAILABLE);
        }
        if (email.equals(auth.getEmail())) {
            throw new BusinessException(UserCode.EMAIL_UNCHANGED);
        }
        if (authMapper.exists(Wrappers.<UserAuthEntity>lambdaQuery().eq(UserAuthEntity::getEmail, email))) {
            throw new BusinessException(UserCode.EMAIL_ALREADY_BOUND);
        }
        userEmailCodeService.sendBindCode(email);
    }

    /** 原子更新邮箱与验证时间，以旧值条件和唯一索引处理并发换绑。 */
    @Override
    public void bindEmail(EmailVerifyDto request) {
        Long userId = UserContextHolder.requireUserId();
        String email = request.getEmail().strip().toLowerCase(Locale.ROOT);
        UserAuthEntity auth = authMapper.selectById(userId);
        if (auth == null || !UserAccountStatus.NORMAL.getCode().equals(auth.getStatus())) {
            throw new BusinessException(UserCode.ACCOUNT_UNAVAILABLE);
        }
        if (email.equals(auth.getEmail())) {
            throw new BusinessException(UserCode.EMAIL_UNCHANGED);
        }
        userEmailCodeService.verifyBindCode(email, request.getCode());
        LocalDateTime verifiedAt = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        var update = Wrappers.<UserAuthEntity>lambdaUpdate()
                .eq(UserAuthEntity::getId, userId)
                .eq(UserAuthEntity::getStatus, UserAccountStatus.NORMAL.getCode())
                .set(UserAuthEntity::getEmail, email)
                .set(UserAuthEntity::getEmailVerifiedAt, verifiedAt)
                .set(UserAuthEntity::getUpdatedAt, verifiedAt);
        // 防止两个并发请求相互覆盖；首次绑定也必须保持原邮箱为空。
        if (auth.getEmail() == null) {
            update.isNull(UserAuthEntity::getEmail);
        } else {
            update.eq(UserAuthEntity::getEmail, auth.getEmail());
        }
        try {
            if (authMapper.update(null, update) != 1) {
                throw new BusinessException(UserCode.EMAIL_BIND_NOT_COMPLETED);
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(UserCode.EMAIL_ALREADY_BOUND);
        }
        log.info("用户邮箱绑定或换绑成功 userId={}", userId);
    }

    /** 本人资料额外包含私密字段和已绑定标识。 */
    @Override
    public MyUserVo getMe() {
        Long userId = UserContextHolder.requireUserId();
        UserAuthEntity auth = authMapper.selectById(userId);
        UserProfileEntity profile = profileMapper.selectById(userId);
        if (auth == null || !UserAccountStatus.NORMAL.getCode().equals(auth.getStatus()) || profile == null) {
            throw new BusinessException(UserCode.USER_NOT_FOUND);
        }
        return MyUserVo.builder()
                .userId(userId.toString())
                .nickname(profile.getNickname())
                .avatarKey(profile.getAvatarKey())
                .avatarUrl(userAvatarService.getAvatarUrl(profile.getAvatarKey()))
                .bio(profile.getBio())
                .genderCode(profile.getGenderCode())
                .birthDate(profile.getBirthDate())
                .realName(profile.getRealName())
                .phone(auth.getPhone())
                .email(auth.getEmail())
                .hasPassword(auth.getPasswordHash() != null)
                .lastLoginIp(auth.getLastLoginIp())
                .lastLoginAt(auth.getLastLoginAt())
                .build();
    }

    /** 仅使用网关已验证的当前用户 ID，明确 SET NULL 以允许清空选填资料。 */
    @Override
    public void updateProfile(ProfileUpdateDto request) {
        Long userId = UserContextHolder.requireUserId();
        UserAuthEntity auth = authMapper.selectById(userId);
        if (auth == null || !UserAccountStatus.NORMAL.getCode().equals(auth.getStatus())) {
            throw new BusinessException(UserCode.ACCOUNT_UNAVAILABLE);
        }
        int updated = profileMapper.update(null, Wrappers.<UserProfileEntity>lambdaUpdate()
                .eq(UserProfileEntity::getUserId, userId)
                .set(UserProfileEntity::getNickname, request.getNickname().strip())
                .set(UserProfileEntity::getBio, StringUtils.hasText(request.getBio()) ? request.getBio().strip() : null)
                .set(UserProfileEntity::getGenderCode, request.getGenderCode() == null ? null : request.getGenderCode().getCode())
                .set(UserProfileEntity::getBirthDate, request.getBirthDate())
                .set(UserProfileEntity::getRealName, StringUtils.hasText(request.getRealName()) ? request.getRealName().strip() : null)
                .set(UserProfileEntity::getUpdatedAt, LocalDateTime.now(ZoneOffset.UTC)));
        if (updated != 1) {
            throw new BusinessException(UserCode.PROFILE_UPDATE_NOT_COMPLETED);
        }
        log.info("用户基础资料更新成功 userId={}", userId);
    }

    /** 公开接口只返回允许展示的字段，并排除禁用账号。 */
    @Override
    public PublicUserVo getPublicProfile(Long userId) {
        UserAuthEntity auth = authMapper.selectById(userId);
        UserProfileEntity profile = profileMapper.selectById(userId);
        if (auth == null || !UserAccountStatus.NORMAL.getCode().equals(auth.getStatus()) || profile == null) {
            throw new BusinessException(UserCode.USER_NOT_FOUND);
        }
        return PublicUserVo.builder()
                .userId(userId.toString())
                .nickname(profile.getNickname())
                .avatarKey(profile.getAvatarKey())
                .avatarUrl(userAvatarService.getAvatarUrl(profile.getAvatarKey()))
                .bio(profile.getBio())
                .build();
    }
}
