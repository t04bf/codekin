package org.ninestick.codekin.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.List;
import org.springframework.data.redis.core.script.RedisScript;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.redis.RedisService;
import org.ninestick.codekin.security.constants.AuthConstants;

/**
 * 验证 JWT 签名、Redis 会话校验和主动撤销的组合行为。
 *
 * @author ninestick
 */
class JwtSessionServiceTest {

    @Test
    void issuedTokenRequiresRedisSessionAndCanBeRevoked() {
        Map<String, String> sessions = new ConcurrentHashMap<>();
        RedisService redis = mock(RedisService.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    List<String> keys = invocation.getArgument(1);
                    sessions.put(keys.get(0), invocation.getArgument(2));
                    sessions.put(keys.get(1), invocation.getArgument(3));
                    return 1L;
                });
        when(redis.get(anyString())).thenAnswer(invocation -> sessions.get(invocation.getArgument(0)));
        when(redis.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long next = Long.parseLong(sessions.getOrDefault(key, "0")) + 1;
            sessions.put(key, Long.toString(next));
            return next;
        });
        doAnswer(invocation -> {
            java.util.Collection<String> keys = invocation.getArgument(0);
            keys.forEach(sessions::remove);
            return null;
        }).when(redis).delete(anyCollection());
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    List<String> keys = invocation.getArgument(1);
                    if (!invocation.getArgument(2).equals(sessions.get(keys.get(0)))
                            || !invocation.getArgument(3).equals(sessions.get(keys.get(1)))) {
                        return 0L;
                    }
                    sessions.put(keys.get(1), invocation.getArgument(4));
                    return 1L;
                });

        JwtProperties properties = new JwtProperties();
        properties.setSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        JwtSessionService service = new JwtSessionService(redis, properties);
        JwtSessionService.IssuedToken first = service.issue(123L);
        String bearer = AuthConstants.BEARER_PREFIX + first.getToken();

        assertEquals(123L, service.requireUserId(bearer));
        JwtSessionService.IssuedToken second = service.refresh(first.getRefreshToken());
        assertEquals(123L, second.getUserId());
        assertThrows(BusinessException.class, () -> service.refresh(first.getRefreshToken()));
        assertEquals(123L, service.requireUserId(AuthConstants.BEARER_PREFIX + second.getToken()));
        service.revoke(AuthConstants.BEARER_PREFIX + second.getToken());
        assertThrows(BusinessException.class, () -> service.requireUserId(bearer));
        assertThrows(BusinessException.class, () -> service.refresh(second.getRefreshToken()));

        JwtSessionService.IssuedToken later = service.issue(123L);
        service.revokeAll(123L);
        assertThrows(BusinessException.class,
                () -> service.requireUserId(AuthConstants.BEARER_PREFIX + later.getToken()));
        assertThrows(BusinessException.class, () -> service.refresh(later.getRefreshToken()));
    }

    @Test
    void missingOrModifiedTokenIsRejected() {
        RedisService redis = mock(RedisService.class);
        JwtProperties properties = new JwtProperties();
        properties.setSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        JwtSessionService service = new JwtSessionService(redis, properties);

        assertThrows(BusinessException.class, () -> service.requireUserId(null));
        assertThrows(BusinessException.class,
                () -> service.requireUserId(AuthConstants.BEARER_PREFIX + "invalid.jwt.value"));
    }
}
