package org.ninestick.codekin.user.domain.vo.info;

import lombok.Builder;
import lombok.Getter;

/**
 * 可公开的用户基本资料，不包含认证与私密字段。
 *
 * @author ninestick
 */
@Getter
@Builder
public class PublicUserVo {

    /** 用户 ID。 */
    private final String userId;

    /** 展示昵称。 */
    private final String nickname;

    /** 头像对象标识。 */
    private final String avatarKey;

    /** 已审核公开头像的短期访问地址。 */
    private final String avatarUrl;

    /** 个人简介。 */
    private final String bio;
}
