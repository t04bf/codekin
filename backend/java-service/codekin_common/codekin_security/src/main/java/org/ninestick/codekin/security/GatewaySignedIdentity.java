package org.ninestick.codekin.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 网关转发给业务服务的一次性短时签名身份。
 *
 * @author ninestick
 */
@Getter
@Builder
@AllArgsConstructor
public class GatewaySignedIdentity {
    /** 全平台用户 ID。 */
    private final String userId;
    /** 当前 JWT 的 jti。 */
    private final String sessionId;
    /** 签发时间，毫秒时间戳。 */
    private final String timestamp;
    /** 防重放随机数。 */
    private final String nonce;
    /** Base64URL 编码的 HMAC-SHA256。 */
    private final String signature;
    /** 网关观测到的客户端连接地址。 */
    private final String clientAddress;
}
