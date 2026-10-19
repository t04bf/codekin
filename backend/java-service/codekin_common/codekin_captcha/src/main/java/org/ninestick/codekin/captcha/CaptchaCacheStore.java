package org.ninestick.codekin.captcha;

import cloud.tianai.captcha.cache.CacheStore;
import cloud.tianai.captcha.common.AnyMap;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.ninestick.codekin.redis.RedisService;
import org.ninestick.codekin.utils.JsonUtils;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 将第三方验证码缓存协议适配到项目 RedisService，图片答案与二次验证凭据均存于 Redis。
 *
 * @author ninestick
 */
public class CaptchaCacheStore implements CacheStore {
    /** 原子读取并删除，保证并发校验或发送只有一次能够消费同一状态。 */
    private static final DefaultRedisScript<String> GET_AND_DELETE = new DefaultRedisScript<>(
            "local v=redis.call('GET',KEYS[1]); if v then redis.call('DEL',KEYS[1]); end; return v",
            String.class);
    /** 组件计数协议需要递增指定步长，并更新毫秒 TTL。 */
    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>(
            "local n=redis.call('INCRBY',KEYS[1],ARGV[1]); redis.call('PEXPIRE',KEYS[1],ARGV[2]); return n",
            Long.class);
    /** 公共 Redis 访问入口。 */
    private final RedisService redisService;

    public CaptchaCacheStore(RedisService redisService) {
        this.redisService = redisService;
    }

    @Override
    public AnyMap getCache(String key) {
        return redisService.getJson(key, AnyMap.class);
    }

    @Override
    public AnyMap getAndRemoveCache(String key) {
        String json = redisService.execute(GET_AND_DELETE, List.of(key));
        return json == null ? null : JsonUtils.fromJson(json, AnyMap.class);
    }

    @Override
    public boolean setCache(String key, AnyMap value, Long timeout, TimeUnit unit) {
        redisService.setJson(key, value, Duration.ofNanos(unit.toNanos(timeout)));
        return true;
    }

    @Override
    public Long incr(String key, long delta, Long timeout, TimeUnit unit) {
        return redisService.execute(INCREMENT, List.of(key), Long.toString(delta),
                Long.toString(unit.toMillis(timeout)));
    }

    @Override
    public Long getLong(String key) {
        String value = redisService.get(key);
        return value == null ? null : Long.valueOf(value);
    }

    /** 连接生命周期由 Spring 的 Redis 配置管理。 */
    @Override
    public void close() {
    }
}
