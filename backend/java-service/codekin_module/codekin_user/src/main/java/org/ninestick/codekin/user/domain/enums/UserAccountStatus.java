package org.ninestick.codekin.user.domain.enums;

import lombok.Getter;

/**
 * 用户账号状态与数据库 status 字段的编码对应关系。
 *
 * @author ninestick
 */
@Getter
public enum UserAccountStatus {
    /** 账号已禁用，不允许登录或展示资料。 */
    DISABLED((byte) 0),

    /** 账号正常，可登录并展示公开资料。 */
    NORMAL((byte) 1);

    /** 数据库中的状态编码。 */
    private final Byte code;

    UserAccountStatus(byte code) {
        this.code = code;
    }

}
