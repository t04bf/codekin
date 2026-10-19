package org.ninestick.codekin.user.service.impl;

import static org.ninestick.codekin.user.sms.UserSmsConstants.ATTEMPT_PREFIX;
import static org.ninestick.codekin.user.sms.UserSmsConstants.BIND_SCOPE;
import static org.ninestick.codekin.user.sms.UserSmsConstants.CODE_HASH_ALGORITHM;
import static org.ninestick.codekin.user.sms.UserSmsConstants.CODE_LENGTH;
import static org.ninestick.codekin.user.sms.UserSmsConstants.CODE_PREFIX;
import static org.ninestick.codekin.user.sms.UserSmsConstants.LOGIN_SCOPE;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.redis.RedisService;
import org.ninestick.codekin.security.UserContextHolder;
import org.ninestick.codekin.sms.SmsCode;
import org.ninestick.codekin.sms.SmsSender;
import org.ninestick.codekin.user.config.UserSmsProperties;
import org.ninestick.codekin.user.domain.enums.UserSmsCode;
import org.ninestick.codekin.user.service.IUserSmsCodeService;
import org.ninestick.codekin.user.sms.UserSmsConstants;
import org.ninestick.codekin.user.verification.VerificationCodeRateLimiter;
import org.ninestick.codekin.user.verification.VerificationCodeRateLimiter.DailyQuota;
import org.springframework.stereotype.Service;

/**
 * 用户业务自行限流、生成和核验验证码；短信模块仅负责投递。
 *
 * @author ninestick
 */
@Service
public class UserSmsCodeServiceImpl implements IUserSmsCodeService {
    /** 当前仅支持中国大陆 11 位手机号。 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    /** 验证码须与服务端生成位数一致。 */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[0-9]{" + CODE_LENGTH + "}$");
    /** 安全随机源，不使用可预测的伪随机数。 */
    private final SecureRandom secureRandom = new SecureRandom();

    /** 当前用户业务的策略。 */
    @Resource
    private UserSmsProperties properties;
    /** 短信厂商发送适配器。 */
    @Resource
    private SmsSender smsSender;
    /** Redis 基础操作。 */
    @Resource
    private RedisService redisService;
    /** 用户业务的发送额度和并发占位。 */
    @Resource
    private VerificationCodeRateLimiter rateLimiter;

    @PostConstruct
    public void validateConfiguration() {
        properties.validate();
    }

    @Override
    public void sendLoginCode(String phoneNumber) {
        send(phoneNumber, LOGIN_SCOPE + ":" + phoneNumber, LOGIN_SCOPE, properties.getLogin());
    }

    @Override
    public void verifyLoginCode(String phoneNumber, String code) {
        verify(phoneNumber, code, LOGIN_SCOPE + ":" + phoneNumber, properties.getLogin());
    }

    @Override
    public void sendBindCode(String phoneNumber) {
        send(phoneNumber, BIND_SCOPE + ":" + UserContextHolder.requireUserId() + ":" + phoneNumber,
                BIND_SCOPE, properties.getBind());
    }

    @Override
    public void verifyBindCode(String phoneNumber, String code) {
        verify(phoneNumber, code, BIND_SCOPE + ":" + UserContextHolder.requireUserId() + ":" + phoneNumber,
                properties.getBind());
    }

    /** 发送前原子占额度；厂商明确拒绝才归还，状态不明时防止重复短信。 */
    private void send(String phoneNumber, String codeSuffix, String scope, UserSmsProperties.Policy policy) {
        if (phoneNumber == null || !PHONE_PATTERN.matcher(phoneNumber).matches()) {
            throw new BusinessException(UserSmsCode.INVALID_PHONE);
        }
        VerificationCodeRateLimiter.Reservation reservation = rateLimiter.reserve(
                VerificationCodeRateLimiter.Request.builder()
                        .cooldownKey(UserSmsConstants.COOLDOWN_PREFIX + scope + ":" + phoneNumber)
                        .reservationTtl(policy.getValidTime().compareTo(policy.getSendInterval()) >= 0
                                ? policy.getValidTime() : policy.getSendInterval())
                        .cooldownCode(UserSmsCode.SEND_COOLDOWN).failureCode(SmsCode.SERVICE_UNAVAILABLE)
                        .quotas(List.of(
                                DailyQuota.builder().keyPrefix(UserSmsConstants.DAILY_PHONE_PREFIX)
                                        .subject(phoneNumber).limit(properties.getDailyPhoneLimit())
                                        .exceededCode(UserSmsCode.PHONE_DAILY_LIMIT).build(),
                                DailyQuota.builder().keyPrefix(UserSmsConstants.DAILY_IP_PREFIX)
                                        .subject(UserContextHolder.requireClientIp()).limit(properties.getDailyIpLimit())
                                        .exceededCode(UserSmsCode.IP_DAILY_LIMIT).build()))
                        .build());
        StringBuilder generated = new StringBuilder(CODE_LENGTH);
        for (int index = 0; index < CODE_LENGTH; index++) {
            generated.append(secureRandom.nextInt(10));
        }
        String code = generated.toString();
        try {
            smsSender.sendCode(phoneNumber, code, policy.getValidTime());
        } catch (BusinessException exception) {
            if (exception.getRecode() == SmsCode.SEND_REJECTED
                    || exception.getRecode() == SmsCode.SEND_TOO_FREQUENT) {
                rateLimiter.release(reservation, true);
            }
            throw exception;
        }
        rateLimiter.complete(reservation, CODE_PREFIX + codeSuffix, ATTEMPT_PREFIX + codeSuffix,
                codeDigest(codeSuffix, code), policy.getValidTime(), policy.getSendInterval());
    }

    /** 验证码摘要比对和删除由 Redis 原子执行，避免并发重放。 */
    private void verify(String phoneNumber, String code, String codeSuffix, UserSmsProperties.Policy policy) {
        if (phoneNumber == null || !PHONE_PATTERN.matcher(phoneNumber).matches()) {
            throw new BusinessException(UserSmsCode.INVALID_PHONE);
        }
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new BusinessException(UserSmsCode.CODE_INVALID);
        }
        String codeKey = CODE_PREFIX + codeSuffix;
        if (redisService.get(codeKey) == null) {
            throw new BusinessException(UserSmsCode.CODE_EXPIRED);
        }
        if (!redisService.incrementWithinLimit(ATTEMPT_PREFIX + codeSuffix,
                policy.getVerifyAttemptLimit(), policy.getValidTime())) {
            throw new BusinessException(UserSmsCode.VERIFY_TOO_FREQUENT);
        }
        if (!redisService.compareAndDelete(codeKey, codeDigest(codeSuffix, code))) {
            throw new BusinessException(UserSmsCode.CODE_INVALID);
        }
    }

    /** HMAC 防止低熵验证码从 Redis 摘要中被离线穷举。 */
    private String codeDigest(String codeSuffix, String code) {
        try {
            Mac mac = Mac.getInstance(CODE_HASH_ALGORITHM);
            mac.init(new SecretKeySpec(properties.getCodeHashSecret().getBytes(StandardCharsets.UTF_8),
                    CODE_HASH_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal((codeSuffix + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("短信验证码摘要算法不可用", exception);
        }
    }
}
