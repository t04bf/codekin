package org.ninestick.codekin.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.Builder;
import lombok.Getter;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.model.common.CommonCode;
import org.ninestick.codekin.redis.RedisService;
import org.ninestick.codekin.security.constants.AuthConstants;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * 短期 JWT 与一次性 Refresh Token 共用 Redis 会话，供网关与业务服务复用。
 *
 * @author ninestick
 */
public class JwtSessionService {
    /** 访问校验所需的会话身份。 */
    private static final String SESSION_PREFIX = "codekin:auth:session:";
    /** 只保存 Refresh Token 的摘要。 */
    private static final String REFRESH_PREFIX = "codekin:auth:refresh:";
    /** 用户级会话代数。 */
    private static final String USER_VERSION_PREFIX = "codekin:auth:user-version:";

    /** 会话身份与刷新摘要同时写入，避免返回只登记了一半的令牌。 */
    private static final DefaultRedisScript<Long> CREATE_SESSION = new DefaultRedisScript<>("""
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])
            redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3])
            return 1
            """, Long.class);
    /** 只有当前会话与旧刷新摘要均匹配时才能原子轮换和续期。 */
    private static final DefaultRedisScript<Long> ROTATE_SESSION = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] or redis.call('GET', KEYS[2]) ~= ARGV[2] then
                return 0
            end
            redis.call('SET', KEYS[2], ARGV[3], 'EX', ARGV[4])
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            return 1
            """, Long.class);

    private final RedisService redisService;
    private final JwtProperties properties;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtSessionService(RedisService redisService, JwtProperties properties) {
        this.redisService = redisService;
        this.properties = properties;
        if (properties.getSecretBase64() == null || properties.getSecretBase64().isBlank()) {
            throw new IllegalArgumentException("必须配置 codekin.security.jwt.secret-base64");
        }
        byte[] secret = Base64.getDecoder().decode(properties.getSecretBase64());
        if (secret.length < 32 || properties.getAccessTtl() == null || properties.getRefreshIdleTtl() == null
                || properties.getSessionMaxLifetime() == null || properties.getAccessTtl().isNegative()
                || properties.getAccessTtl().isZero() || properties.getRefreshIdleTtl().isNegative()
                || properties.getRefreshIdleTtl().isZero() || properties.getSessionMaxLifetime().isNegative()
                || properties.getSessionMaxLifetime().isZero()
                || properties.getAccessTtl().compareTo(properties.getRefreshIdleTtl()) >= 0
                || properties.getRefreshIdleTtl().compareTo(properties.getSessionMaxLifetime()) >= 0) {
            throw new IllegalArgumentException("须满足 0 < Access TTL < Refresh 空闲期 < 会话上限，JWT 密钥至少 32 字节");
        }
        SecretKey key = new SecretKeySpec(secret, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.getIssuer()));
        this.decoder = jwtDecoder;
    }

    /** 登录创建会话；绝对过期时间只在此设置。 */
    public IssuedToken issue(Long userId) {
        Instant now = Instant.now();
        String sessionId = UUID.randomUUID().toString();
        Instant absoluteExpiresAt = now.plus(properties.getSessionMaxLifetime());
        Duration idle = properties.getRefreshIdleTtl();
        String refreshToken = newRefreshToken(sessionId);
        String sessionValue = userId + ":" + currentVersion(userId) + ":" + absoluteExpiresAt.toEpochMilli();
        IssuedToken issued = signedTokens(userId, sessionId, refreshToken, now, absoluteExpiresAt, idle);
        Long created = redisService.execute(CREATE_SESSION,
                List.of(SESSION_PREFIX + sessionId, REFRESH_PREFIX + sessionId),
                sessionValue, digest(refreshToken), Long.toString(idle.toSeconds()));
        if (!Long.valueOf(1L).equals(created)) {
            throw new IllegalStateException("Redis 会话创建失败");
        }
        return issued;
    }

    /** 原子轮换 Refresh Token，仅刷新空闲时间，不延长会话绝对上限。 */
    public IssuedToken refresh(String refreshToken) {
        if (refreshToken == null || !refreshToken.matches("^[0-9a-f-]{36}\\.[A-Za-z0-9_-]{43}$")) {
            throw new BusinessException(CommonCode.UNAUTHORIZED);
        }
        String sessionId = refreshToken.substring(0, refreshToken.indexOf('.'));
        String sessionValue = redisService.get(SESSION_PREFIX + sessionId);
        SessionState state = parseSession(sessionValue);
        Instant now = Instant.now();
        if (state == null || !now.isBefore(state.absoluteExpiresAt)
                || state.version != currentVersion(state.userId)) {
            throw new BusinessException(CommonCode.UNAUTHORIZED);
        }
        Duration ttl = properties.getRefreshIdleTtl().compareTo(Duration.between(now, state.absoluteExpiresAt)) < 0
                ? properties.getRefreshIdleTtl() : Duration.between(now, state.absoluteExpiresAt);
        if (ttl.toSeconds() < 1) {
            throw new BusinessException(CommonCode.UNAUTHORIZED);
        }
        String nextRefreshToken = newRefreshToken(sessionId);
        IssuedToken issued = signedTokens(state.userId, sessionId, nextRefreshToken, now,
                state.absoluteExpiresAt, ttl);
        Long rotated = redisService.execute(ROTATE_SESSION,
                List.of(SESSION_PREFIX + sessionId, REFRESH_PREFIX + sessionId),
                sessionValue, digest(refreshToken), digest(nextRefreshToken), Long.toString(ttl.toSeconds()));
        if (!Long.valueOf(1L).equals(rotated)) {
            throw new BusinessException(CommonCode.UNAUTHORIZED);
        }
        return issued;
    }

    /** 校验签名、期限、Redis 会话与用户级撤销状态。 */
    public AuthenticatedUser requireSession(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(AuthConstants.BEARER_PREFIX)) {
            throw new BusinessException(CommonCode.UNAUTHORIZED);
        }
        try {
            Jwt jwt = decoder.decode(authorizationHeader.substring(AuthConstants.BEARER_PREFIX.length()));
            String sessionId = jwt.getId();
            Long userId = Long.valueOf(jwt.getSubject());
            String value = redisService.get(SESSION_PREFIX + sessionId);
            SessionState state = parseSession(value);
            if (state == null) {
                // 旧版 Redis 会话允许用到原 JWT 到期，不具备刷新能力。
                if (!userId.toString().equals(value) || currentVersion(userId) != 0) {
                    throw new BusinessException(CommonCode.UNAUTHORIZED);
                }
            } else if (!userId.equals(state.userId) || !Instant.now().isBefore(state.absoluteExpiresAt)
                    || state.version != currentVersion(userId)) {
                throw new BusinessException(CommonCode.UNAUTHORIZED);
            }
            return AuthenticatedUser.builder().userId(userId).sessionId(sessionId).build();
        } catch (JwtException | NumberFormatException | NullPointerException exception) {
            throw new BusinessException(CommonCode.UNAUTHORIZED);
        }
    }

    /** 兼容已有调用方。 */
    public Long requireUserId(String authorizationHeader) {
        return requireSession(authorizationHeader).getUserId();
    }

    /** 当前会话的访问与刷新凭据同时失效。 */
    public void revokeSession(String sessionId) {
        redisService.delete(List.of(SESSION_PREFIX + sessionId, REFRESH_PREFIX + sessionId));
    }

    /** 兼容已有 Authorization 头调用方。 */
    public void revoke(String authorizationHeader) {
        revokeSession(requireSession(authorizationHeader).getSessionId());
    }

    /** 密码变更后撤销该用户所有旧会话。 */
    public void revokeAll(Long userId) {
        redisService.increment(USER_VERSION_PREFIX + userId);
    }

    private long currentVersion(Long userId) {
        String value = redisService.get(USER_VERSION_PREFIX + userId);
        return value == null ? 0 : Long.parseLong(value);
    }

    private IssuedToken signedTokens(Long userId, String sessionId, String refreshToken, Instant now,
            Instant absoluteExpiresAt, Duration refreshTtl) {
        Instant accessExpiresAt = now.plus(properties.getAccessTtl());
        if (accessExpiresAt.isAfter(absoluteExpiresAt)) {
            accessExpiresAt = absoluteExpiresAt;
        }
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(properties.getIssuer()).subject(userId.toString())
                .id(sessionId).issuedAt(now).expiresAt(accessExpiresAt).build();
        String accessToken = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return IssuedToken.builder().userId(userId).token(accessToken).refreshToken(refreshToken)
                .expiresAt(accessExpiresAt).refreshExpiresAt(now.plus(refreshTtl))
                .sessionExpiresAt(absoluteExpiresAt).build();
    }

    private String newRefreshToken(String sessionId) {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        return sessionId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Refresh Token 摘要算法不可用", exception);
        }
    }

    private SessionState parseSession(String value) {
        if (value == null) {
            return null;
        }
        String[] fields = value.split(":", -1);
        if (fields.length != 3) {
            return null;
        }
        try {
            return new SessionState(Long.valueOf(fields[0]), Long.parseLong(fields[1]),
                    Instant.ofEpochMilli(Long.parseLong(fields[2])));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** Redis 会话中不变的身份、撤销代数及绝对截止时间。 */
    private static final class SessionState {
        private final Long userId;
        private final long version;
        private final Instant absoluteExpiresAt;

        private SessionState(Long userId, long version, Instant absoluteExpiresAt) {
            this.userId = userId;
            this.version = version;
            this.absoluteExpiresAt = absoluteExpiresAt;
        }
    }

    /** 返回给业务层的令牌对。 */
    @Getter
    @Builder
    public static class IssuedToken {
        /** 全平台用户 ID。 */
        private final Long userId;
        /** 短期 JWT。 */
        private final String token;
        /** 一次性不透明刷新令牌。 */
        private final String refreshToken;
        /** JWT 到期时间。 */
        private final Instant expiresAt;
        /** 当前刷新令牌的空闲截止时间。 */
        private final Instant refreshExpiresAt;
        /** 会话不可延长的到期时间。 */
        private final Instant sessionExpiresAt;
    }
}
