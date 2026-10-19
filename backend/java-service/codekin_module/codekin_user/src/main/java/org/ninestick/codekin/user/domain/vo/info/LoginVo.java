package org.ninestick.codekin.user.domain.vo.info;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/**
 * 登录或刷新成功后的令牌对；用户 ID 以字符串形式传输。
 *
 * @author ninestick
 */
@Getter
@Builder
public class LoginVo {

    /** 全平台用户 ID。 */
    private final String userId;

    /** 用于 Authorization: Bearer 的 JWT。 */
    private final String accessToken;

    /** 一次性 Refresh Token，应由客户端安全保存。 */
    private final String refreshToken;

    /** 令牌类型。 */
    private final String tokenType;

    /** 到期时间。 */
    private final Instant expiresAt;

    /** 当前 Refresh Token 的空闲到期时间。 */
    private final Instant refreshExpiresAt;

    /** 当前会话不可延长的到期时间。 */
    private final Instant sessionExpiresAt;
}
