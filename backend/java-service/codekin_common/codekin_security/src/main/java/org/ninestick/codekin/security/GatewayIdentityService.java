package org.ninestick.codekin.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.model.common.CommonCode;

/**
 * 网关签发短时身份头，业务服务验签；不复用用户 JWT 的密钥或解析逻辑。
 *
 * @author ninestick
 */
public class GatewayIdentityService {
    /** HMAC 算法。 */
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    /** 接收网关与服务之间最多五秒的未来时间偏差。 */
    private static final long MAX_FUTURE_SKEW_MILLIS = 5_000L;

    /** 网关与服务共享的独立 HMAC 密钥。 */
    private final byte[] secret;
    /** 签名身份的最大年龄。 */
    private final Duration maxAge;

    public GatewayIdentityService(GatewayContextProperties properties) {
        if (properties.getSecretBase64() == null || properties.getSecretBase64().isBlank()) {
            throw new IllegalArgumentException("必须配置 codekin.security.gateway-context.secret-base64");
        }
        this.secret = Base64.getDecoder().decode(properties.getSecretBase64());
        this.maxAge = properties.getMaxAge();
        if (secret.length < 32 || maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
            throw new IllegalArgumentException("网关身份头密钥至少 32 字节且有效期必须大于零");
        }
    }

    /** 将 JWT 和 Redis 已确认的身份绑定到请求方法与原始请求目标。 */
    public GatewaySignedIdentity sign(AuthenticatedUser user, String clientAddress, String method, String target) {
        String userId = user == null ? "" : user.getUserId().toString();
        String sessionId = user == null ? "" : user.getSessionId();
        String timestamp = Long.toString(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString();
        String signature = calculateSignature(userId, sessionId, timestamp, nonce, clientAddress, method, target);
        return GatewaySignedIdentity.builder()
                .userId(userId)
                .sessionId(sessionId)
                .timestamp(timestamp)
                .nonce(nonce)
                .clientAddress(clientAddress)
                .signature(signature)
                .build();
    }

    /** 验证时间、请求绑定和 HMAC，失败统一返回未认证业务错误。 */
    public VerifiedGatewayRequest verify(GatewaySignedIdentity identity, String method, String target) {
        try {
            String userIdText = identity.getUserId();
            boolean anonymous = userIdText != null && userIdText.isEmpty();
            long userId = anonymous ? 0L : Long.parseLong(userIdText);
            long issuedAt = Long.parseLong(identity.getTimestamp());
            long age = System.currentTimeMillis() - issuedAt;
            if ((!anonymous && userId <= 0) || identity.getSessionId() == null
                    || (anonymous ? !identity.getSessionId().isEmpty() : identity.getSessionId().isBlank())
                    || identity.getClientAddress() == null || identity.getClientAddress().isBlank()
                    || identity.getClientAddress().length() > 45
                    || identity.getNonce() == null || identity.getNonce().isBlank()
                    || age < -MAX_FUTURE_SKEW_MILLIS || age > maxAge.toMillis()) {
                throw new BusinessException(CommonCode.UNAUTHORIZED);
            }
            byte[] actual = Base64.getUrlDecoder().decode(identity.getSignature());
            byte[] expected = Base64.getUrlDecoder().decode(calculateSignature(identity.getUserId(),
                    identity.getSessionId(), identity.getTimestamp(), identity.getNonce(),
                    identity.getClientAddress(), method, target));
            if (!MessageDigest.isEqual(actual, expected)) {
                throw new BusinessException(CommonCode.UNAUTHORIZED);
            }
            AuthenticatedUser user = anonymous ? null : AuthenticatedUser.builder()
                    .userId(userId).sessionId(identity.getSessionId()).build();
            return VerifiedGatewayRequest.builder().user(user)
                    .clientAddress(identity.getClientAddress()).build();
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(CommonCode.UNAUTHORIZED);
        }
    }

    /** 每次创建 Mac 实例，避免其非线程安全状态跨请求复用。 */
    private String calculateSignature(String userId, String sessionId, String timestamp, String nonce,
            String clientAddress, String method, String target) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            String payload = userId + "\n" + sessionId + "\n" + timestamp + "\n" + nonce + "\n"
                    + clientAddress + "\n"
                    + method + "\n" + target;
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法签发网关身份头", exception);
        }
    }

    public Duration getMaxAge() {
        return maxAge;
    }
}
