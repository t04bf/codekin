package org.ninestick.codekin.user.service.impl;

import static org.ninestick.codekin.user.email.UserEmailConstants.BIND_PREFIX;
import static org.ninestick.codekin.user.email.UserEmailConstants.DAILY_ADDRESS_PREFIX;
import static org.ninestick.codekin.user.email.UserEmailConstants.DAILY_IP_PREFIX;
import static org.ninestick.codekin.user.email.UserEmailConstants.LOGIN_PREFIX;

import jakarta.annotation.Resource;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.ninestick.codekin.email.EmailSender;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.redis.RedisService;
import org.ninestick.codekin.security.UserContextHolder;
import org.ninestick.codekin.user.config.UserEmailProperties;
import org.ninestick.codekin.user.domain.enums.UserEmailCode;
import org.ninestick.codekin.user.service.IUserEmailCodeService;
import org.ninestick.codekin.user.verification.VerificationCodeRateLimiter;
import org.ninestick.codekin.user.verification.VerificationCodeRateLimiter.DailyQuota;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.Assert;

/**
 * 邮箱登录及绑定验证码的生成、限额、摘要存储与一次性核验。
 *
 * @author ninestick
 */
@Slf4j
@Service
public class UserEmailCodeServiceImpl implements IUserEmailCodeService {
    /** 邮件发送适配器不负责生成或保存验证码。 */
    @Resource
    private EmailSender emailSender;
    /** 基础 Redis 原子操作。 */
    @Resource
    private RedisService redisService;
    /** 邮箱与短信共用的额度占用和验证码登记组件。 */
    @Resource
    private VerificationCodeRateLimiter rateLimiter;
    /** 验证码业务参数。 */
    @Resource
    private UserEmailProperties properties;
    /** 加密安全的验证码随机源。 */
    private final SecureRandom secureRandom = new SecureRandom();

    /** 部署时拒绝无效策略；邮箱凭据允许稍后填写，但发送时会明确失败。 */
    @PostConstruct
    public void validateConfiguration() {
        Assert.isTrue(properties.getValidTime() != null && properties.getValidTime().toSeconds() > 0
                        && properties.getSendInterval() != null && properties.getSendInterval().toSeconds() > 0
                        && properties.getDailyAddressLimit() > 0 && properties.getDailyIpLimit() > 0
                        && properties.getVerifyAttemptLimit() > 0,
                "邮箱验证码限额及有效时间必须为正数");
    }

    @Override
    public void sendLoginCode(String email) {
        send(email, LOGIN_PREFIX, null);
    }

    @Override
    public void verifyLoginCode(String email, String code) {
        verify(email, code, LOGIN_PREFIX, null);
    }

    @Override
    public void sendBindCode(String email) {
        send(email, BIND_PREFIX, UserContextHolder.requireUserId());
    }

    @Override
    public void verifyBindCode(String email, String code) {
        verify(email, code, BIND_PREFIX, UserContextHolder.requireUserId());
    }

    /** 用途隔离验证码和冷却；日额度跨用途共享，绑定验证码同时限定用户。 */
    private void send(String email, String prefix, Long bindingUserId) {
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BusinessException(UserEmailCode.INVALID_EMAIL);
        }
        if (!StringUtils.hasText(properties.getCodeHashSecret())
                || properties.getCodeHashSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            log.error("邮箱验证码摘要密钥未配置或长度不足");
            throw new BusinessException(UserEmailCode.SERVICE_UNAVAILABLE);
        }
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        String addressKey = digest(normalized);
        String codeSuffix = bindingUserId == null ? addressKey : bindingUserId + ":" + addressKey;
        VerificationCodeRateLimiter.Reservation reservation = rateLimiter.reserve(
                VerificationCodeRateLimiter.Request.builder()
                        .cooldownKey(prefix + "cooldown:" + addressKey)
                        .reservationTtl(properties.getValidTime().compareTo(properties.getSendInterval()) >= 0
                                ? properties.getValidTime() : properties.getSendInterval())
                        .cooldownCode(UserEmailCode.SEND_COOLDOWN).failureCode(UserEmailCode.SERVICE_UNAVAILABLE)
                        .quotas(List.of(
                                DailyQuota.builder().keyPrefix(DAILY_ADDRESS_PREFIX)
                                        .subject(addressKey).limit(properties.getDailyAddressLimit())
                                        .exceededCode(UserEmailCode.ADDRESS_LIMIT).build(),
                                DailyQuota.builder().keyPrefix(DAILY_IP_PREFIX)
                                        .subject(digest(UserContextHolder.requireClientIp()))
                                        .limit(properties.getDailyIpLimit())
                                        .exceededCode(UserEmailCode.IP_LIMIT).build()))
                        .build());
        String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
        try {
            emailSender.sendCode(normalized, code, properties.getValidTime());
        } catch (RuntimeException exception) {
            // SMTP 异常可能发生在服务端已接收之后，保持原有的额度保留策略。
            rateLimiter.release(reservation, false);
            log.error("邮箱验证码投递失败 exceptionType={}", exception.getClass().getSimpleName());
            throw new BusinessException(UserEmailCode.SERVICE_UNAVAILABLE);
        }
        // 登录摘要保持兼容；绑定摘要额外包含用途和用户，不能复制到其他用途或账号。
        String digestSubject = bindingUserId == null ? normalized : prefix + bindingUserId + ":" + normalized;
        rateLimiter.complete(reservation, prefix + "code:" + codeSuffix, prefix + "attempt:" + codeSuffix,
                digest(digestSubject + ":" + code), properties.getValidTime(), properties.getSendInterval());
    }

    /** 核验次数与一次性消费均限定在相同用途、当前用户和目标邮箱内。 */
    private void verify(String email, String code, String prefix, Long bindingUserId) {
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BusinessException(UserEmailCode.INVALID_EMAIL);
        }
        if (code == null || !code.matches("^\\d{6}$")) {
            throw new BusinessException(UserEmailCode.CODE_INVALID);
        }
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        String addressKey = digest(normalized);
        String codeSuffix = bindingUserId == null ? addressKey : bindingUserId + ":" + addressKey;
        String codeKey = prefix + "code:" + codeSuffix;
        if (redisService.get(codeKey) == null) {
            throw new BusinessException(UserEmailCode.CODE_EXPIRED);
        }
        if (!redisService.incrementWithinLimit(prefix + "attempt:" + codeSuffix,
                properties.getVerifyAttemptLimit(), properties.getValidTime())) {
            throw new BusinessException(UserEmailCode.VERIFY_TOO_FREQUENT);
        }
        String digestSubject = bindingUserId == null ? normalized : prefix + bindingUserId + ":" + normalized;
        if (!redisService.compareAndDelete(codeKey, digest(digestSubject + ":" + code))) {
            throw new BusinessException(UserEmailCode.CODE_INVALID);
        }
    }

    /** HMAC 摘要既保护 Redis 键里的邮箱，也防止六位码被离线枚举。 */
    private String digest(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getCodeHashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("邮箱验证码摘要算法不可用", exception);
        }
    }
}
