package org.ninestick.codekin.security.constants;

/**
 * 认证协议中由业务服务和网关共用的常量。
 *
 * @author ninestick
 */
public final class AuthConstants {

    /** Authorization 请求头中的 Bearer 认证类型。 */
    public static final String BEARER_TOKEN_TYPE = "Bearer";

    /** Bearer 类型和令牌之间包含一个空格。 */
    public static final String BEARER_PREFIX = BEARER_TOKEN_TYPE + " ";

    /** 网关签名身份中的用户 ID 请求头。 */
    public static final String GATEWAY_USER_ID_HEADER = "X-Codekin-User-Id";

    /** 网关签名身份中的 Redis/JWT 会话 ID 请求头。 */
    public static final String GATEWAY_SESSION_ID_HEADER = "X-Codekin-Session-Id";

    /** 网关签名身份中的签发毫秒时间戳请求头。 */
    public static final String GATEWAY_TIMESTAMP_HEADER = "X-Codekin-Auth-Timestamp";

    /** 网关签名身份中的一次性随机数请求头。 */
    public static final String GATEWAY_NONCE_HEADER = "X-Codekin-Auth-Nonce";

    /** 网关签名身份中的 HMAC-SHA256 请求头。 */
    public static final String GATEWAY_SIGNATURE_HEADER = "X-Codekin-Auth-Signature";

    /** 网关观测到的客户端连接地址，参与身份头签名。 */
    public static final String GATEWAY_CLIENT_ADDRESS_HEADER = "X-Codekin-Client-Address";

    private AuthConstants() {
    }
}
