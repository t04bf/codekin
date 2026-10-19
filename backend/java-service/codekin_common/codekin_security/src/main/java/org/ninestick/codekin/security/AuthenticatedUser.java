package org.ninestick.codekin.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 请求中的已认证身份，用户 ID 是 user_auth.id，会话 ID 是 JWT jti。
 *
 * @author ninestick
 */
@Getter
@Builder
@AllArgsConstructor
public class AuthenticatedUser {
    /** 全平台用户 ID。 */
    private final Long userId;

    /** 用于注销当前 Redis 会话的 JWT jti。 */
    private final String sessionId;
}
