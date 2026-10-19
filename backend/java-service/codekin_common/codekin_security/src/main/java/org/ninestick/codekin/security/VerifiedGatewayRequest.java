package org.ninestick.codekin.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 业务服务验签后的请求身份及客户端地址；公开请求的 user 可以为空。
 *
 * @author ninestick
 */
@Getter
@Builder
@AllArgsConstructor
public class VerifiedGatewayRequest {
    /** 已认证用户；公开请求为空。 */
    private final AuthenticatedUser user;
    /** 网关观测并签名的客户端连接 IP。 */
    private final String clientAddress;
}
