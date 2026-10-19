package org.ninestick.codekin.redis;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.ninestick.codekin.utils.JsonUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 基于字符串模板的公共 Redis 操作，避免共享模块引入不安全的多态反序列化。
 *
 * @author ninestick
 */
public class RedisService {

    /** 原子计数并只在首次计数时设置窗口过期时间。 */
    private static final DefaultRedisScript<Long> INCREMENT_WITH_LIMIT = new DefaultRedisScript<>(
            "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[2]); end; "
                    + "if n<=tonumber(ARGV[1]) then return 1 else return 0 end", Long.class);

    /** 仅当当前值匹配时原子删除，防止并发核验重复消费或误删新值。 */
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE = new DefaultRedisScript<>(
            "if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1]) else return 0 end",
            Long.class);

    /** 字符串 Redis 访问入口，仅在基础设施模块内持有。 */
    private final StringRedisTemplate redisTemplate;

    public RedisService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 设置带过期时间的字符串；过期时间必须为正数。 */
    public void set(String key, String value, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Redis TTL 必须大于零");
        }
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    /** 获取字符串值；不存在时返回 null。 */
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /** 删除键，返回是否实际删除。 */
    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    /** 原子递增整数值，键不存在时从零开始。 */
    public long increment(String key) {
        Long value = redisTemplate.opsForValue().increment(key);
        if (value == null) {
            throw new IllegalStateException("Redis 计数更新失败");
        }
        return value;
    }

    /** 批量删除键，由 Redis 单条 DEL 命令完成。 */
    public void delete(Collection<String> keys) {
        redisTemplate.delete(keys);
    }

    /** 返回剩余秒数；键不存在或未设置过期时返回 Redis 的负数标记。 */
    public Long getExpireSeconds(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /** 执行调用方定义的原子脚本，场景键规则和结果解释由调用方持有。 */
    public <T> T execute(RedisScript<T> script, List<String> keys, Object... arguments) {
        return redisTemplate.execute(script, keys, arguments);
    }

    /** 比对并原子消费一个字符串值；不存在或不匹配时返回 false。 */
    public boolean compareAndDelete(String key, String expectedValue) {
        Long deleted = redisTemplate.execute(COMPARE_AND_DELETE, Collections.singletonList(key), expectedValue);
        return Long.valueOf(1L).equals(deleted);
    }

    /** 仅在键不存在时写入并设置过期时间。 */
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Redis TTL 必须大于零");
        }
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, ttl));
    }

    /** 原子计数并判断是否仍处于窗口限额内，超出限额的请求也计入窗口。 */
    public boolean incrementWithinLimit(String key, long limit, Duration window) {
        if (limit <= 0 || window.toSeconds() <= 0) {
            throw new IllegalArgumentException("Redis 限额必须大于零且窗口至少为一秒");
        }
        Long allowed = redisTemplate.execute(INCREMENT_WITH_LIMIT, Collections.singletonList(key),
                Long.toString(limit), Long.toString(window.toSeconds()));
        return Long.valueOf(1L).equals(allowed);
    }

    /** 保存 JSON 数据，适用于业务方明确指定反序列化类型的场景。 */
    public void setJson(String key, Object value, Duration ttl) {
        set(key, JsonUtils.toJson(value), ttl);
    }

    /** 获取 JSON 数据；键不存在时返回 null。 */
    public <T> T getJson(String key, Class<T> type) {
        String value = get(key);
        return value == null ? null : JsonUtils.fromJson(value, type);
    }
}
