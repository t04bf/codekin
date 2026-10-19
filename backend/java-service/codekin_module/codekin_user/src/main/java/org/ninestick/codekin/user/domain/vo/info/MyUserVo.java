package org.ninestick.codekin.user.domain.vo.info;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * 当前用户可查看的完整基础资料与绑定标识。
 *
 * @author ninestick
 */
@Getter
@Builder
public class MyUserVo {

    /** 用户 ID。 */
    private final String userId;

    /** 展示昵称。 */
    private final String nickname;

    /** 头像对象标识。 */
    private final String avatarKey;

    /** 文件服务签发的短期头像访问地址，不在前端持久化。 */
    private final String avatarUrl;

    /** 个人简介。 */
    private final String bio;

    /** 用户自填性别。 */
    private final String genderCode;

    /** 用户自填生日。 */
    private final LocalDate birthDate;

    /** 用户自填真实姓名。 */
    private final String realName;

    /** 已绑定手机号。 */
    private final String phone;

    /** 已绑定邮箱。 */
    private final String email;

    /** 是否已设置密码，个人信息接口始终返回 true 或 false；不返回密码散列或算法细节。 */
    private final boolean hasPassword;

    /** 最近一次成功登录 IP。 */
    private final String lastLoginIp;

    /** 最近一次成功登录 UTC 时间。 */
    private final LocalDateTime lastLoginAt;
}
