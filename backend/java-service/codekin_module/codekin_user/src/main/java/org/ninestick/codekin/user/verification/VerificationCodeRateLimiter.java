package org.ninestick.codekin.user.verification;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.model.common.Recode;
import org.ninestick.codekin.redis.RedisService;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * 短信与邮箱共用的发送限流工具，封装自然日额度、发送占位、回滚和完成登记。
 * 调用方声明键和业务错误码，无需解释 Redis 脚本结果。
 *
 * @author ninestick
 */
@Component
@Slf4j
public class VerificationCodeRateLimiter {
    /** 自然日配额统一按北京时间计算。 */
    private static final ZoneId DAILY_ZONE = ZoneId.of("Asia/Shanghai");
    /** 占位存在时返回的内部脚本标记。 */
    private static final long COOLDOWN = -1L;
    /** 所有限额检查通过时的内部脚本标记。 */
    private static final long ALLOWED = 0L;
    /** 先检查全部限额，再原子占用；正数结果表示超限额度在请求列表中的一基下标。 */
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then return -1 end
            for i = 2, #KEYS do
                if tonumber(redis.call('GET', KEYS[i]) or '0') >= tonumber(ARGV[i + 2]) then
                    return i - 1
                end
            end
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
            for i = 2, #KEYS do
                if redis.call('INCR', KEYS[i]) == 1 then
                    redis.call('EXPIRE', KEYS[i], ARGV[3])
                end
            end
            return 0
            """, Long.class);
    /** 只有当前占位持有者能释放；是否归还额度由调用方按发送结果是否确定决定。 */
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('DEL', KEYS[1])
            if ARGV[2] == 'true' then
                for i = 2, #KEYS do
                    if tonumber(redis.call('GET', KEYS[i]) or '0') > 0 then
                        redis.call('DECR', KEYS[i])
                    end
                end
            end
            return 1
            """, Long.class);
    /** 发送受理后，在一次原子操作中写摘要、清核验计数并开始冷却。 */
    private static final DefaultRedisScript<Long> COMPLETE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3])
            redis.call('DEL', KEYS[3])
            redis.call('SET', KEYS[1], 'cooldown', 'EX', ARGV[4])
            return 1
            """, Long.class);

    /** 所有 Redis 操作通过公共基础工具执行。 */
    @Resource
    private RedisService redisService;

    /** 声明式申请额度；不允许发送时直接抛出调用方提供的业务错误。 */
    public Reservation reserve(Request request) {
        Assert.notNull(request, "发送额度请求不能为空");
        Assert.hasText(request.cooldownKey, "发送冷却键不能为空");
        Assert.isTrue(request.reservationTtl != null && request.reservationTtl.toSeconds() > 0,
                "发送占位时间必须至少一秒");
        Assert.notEmpty(request.quotas, "发送额度不能为空");
        Assert.notNull(request.cooldownCode, "发送冷却错误码不能为空");
        Assert.notNull(request.failureCode, "发送失败错误码不能为空");
        ZonedDateTime now = ZonedDateTime.now(DAILY_ZONE);
        String date = now.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        long dailyTtl = Math.max(1L,
                Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(DAILY_ZONE)).toSeconds());
        String token = UUID.randomUUID().toString();
        List<String> keys = new ArrayList<>();
        List<String> arguments = new ArrayList<>();
        keys.add(request.cooldownKey);
        arguments.add(token);
        arguments.add(Long.toString(request.reservationTtl.toSeconds()));
        arguments.add(Long.toString(dailyTtl));
        for (DailyQuota quota : request.quotas) {
            Assert.hasText(quota.keyPrefix, "日额度键前缀不能为空");
            Assert.hasText(quota.subject, "日额度统计对象不能为空");
            Assert.isTrue(quota.limit > 0, "日额度必须大于零");
            Assert.notNull(quota.exceededCode, "日额度错误码不能为空");
            keys.add(quota.keyPrefix + date + ":" + quota.subject);
            arguments.add(Long.toString(quota.limit));
        }
        Long result = redisService.execute(RESERVE, keys, arguments.toArray());
        if (Long.valueOf(COOLDOWN).equals(result)) {
            Long remaining = redisService.getExpireSeconds(request.cooldownKey);
            throw new BusinessException(request.cooldownCode, "请 {} 秒后重试",
                    remaining == null || remaining < 1 ? 1 : remaining);
        }
        if (result != null && result > 0 && result <= request.quotas.size()) {
            throw new BusinessException(request.quotas.get(result.intValue() - 1).exceededCode);
        }
        if (!Long.valueOf(ALLOWED).equals(result)) {
            log.error("验证码发送额度脚本返回异常 result={}", result);
            throw new BusinessException(request.failureCode);
        }
        return Reservation.builder().keys(List.copyOf(keys)).token(token)
                .failureCode(request.failureCode).build();
    }

    /** 释放本次占位；只有明确未投递时才传入 refundQuota=true 归还日额度。 */
    public void release(Reservation reservation, boolean refundQuota) {
        redisService.execute(RELEASE, reservation.keys, reservation.token, Boolean.toString(refundQuota));
    }

    /** 完成验证码登记；旧请求或超时响应不能覆盖后来发送的新验证码。 */
    public void complete(Reservation reservation, String codeKey, String attemptKey, String codeDigest,
            Duration validTime, Duration cooldown) {
        Assert.isTrue(validTime != null && validTime.toSeconds() > 0
                && cooldown != null && cooldown.toSeconds() > 0, "验证码有效期和冷却时间必须至少一秒");
        Long result = redisService.execute(COMPLETE, List.of(reservation.keys.getFirst(), codeKey, attemptKey),
                reservation.token, codeDigest, Long.toString(validTime.toSeconds()),
                Long.toString(cooldown.toSeconds()));
        if (!Long.valueOf(1L).equals(result)) {
            log.error("验证码发送已受理但原子登记失败");
            throw new BusinessException(reservation.failureCode);
        }
    }

    /**
     * 一次发送所需的冷却和日额度声明。
     *
     * @author ninestick
     */
    @Getter
    @Builder
    public static class Request {
        /** 按业务场景隔离的冷却键。 */
        private final String cooldownKey;
        /** 等待投递结果时的占位有效期。 */
        private final Duration reservationTtl;
        /** 已在冷却或发送中时使用的业务错误码。 */
        private final Recode cooldownCode;
        /** Redis 返回异常或完成登记失败时使用的错误码。 */
        private final Recode failureCode;
        /** 按顺序检查的自然日额度，可按业务增加统计维度。 */
        private final List<DailyQuota> quotas;
    }

    /**
     * 一个自然日额度的统计规则。
     *
     * @author ninestick
     */
    @Getter
    @Builder
    public static class DailyQuota {
        /** 日期之前的键前缀，例如 sms:daily:phone:。 */
        private final String keyPrefix;
        /** 统计对象，例如手机号、邮箱摘要或客户端 IP。 */
        private final String subject;
        /** 该统计对象一天的上限。 */
        private final long limit;
        /** 超限时直接抛出的业务错误码。 */
        private final Recode exceededCode;
    }

    /**
     * 本次发送持有的占位，用于阻止其他请求误回滚或覆盖结果。
     *
     * @author ninestick
     */
    @Getter
    @Builder
    public static class Reservation {
        /** 第一项为冷却键，其余为已经占用的日额度键。 */
        private final List<String> keys;
        /** 仅当前请求持有的随机令牌。 */
        private final String token;
        /** 本次发送登记失败对应的业务错误码。 */
        private final Recode failureCode;
    }
}
