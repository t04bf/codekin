package org.ninestick.codekin.user.domain.enums;

import org.ninestick.codekin.model.common.Recode;

/**
 * 用户域的稳定业务错误码。
 *
 * @author ninestick
 */
public enum UserCode implements Recode {
    INVALID_CREDENTIALS("USER_INVALID_CREDENTIALS", "账号或密码错误"),
    LOGIN_NOT_COMPLETED("USER_LOGIN_NOT_COMPLETED", "登录未完成，请稍后重试"),
    USER_NOT_FOUND("USER_NOT_FOUND", "用户不存在"),
    ACCOUNT_UNAVAILABLE("USER_ACCOUNT_UNAVAILABLE", "账号当前不可用"),
    PHONE_UNVERIFIED("USER_PHONE_UNVERIFIED", "该账号的手机号尚未完成绑定验证"),
    PHONE_ALREADY_BOUND("USER_PHONE_ALREADY_BOUND", "该手机号已被其他账号绑定"),
    PHONE_UNCHANGED("USER_PHONE_UNCHANGED", "该手机号已经绑定当前账号"),
    PHONE_BIND_NOT_COMPLETED("USER_PHONE_BIND_NOT_COMPLETED", "手机号绑定未完成，请重试"),
    EMAIL_UNVERIFIED("USER_EMAIL_UNVERIFIED", "该账号的邮箱尚未完成验证"),
    /** 邮箱唯一索引冲突，不能合并或接管其他账号。 */
    EMAIL_ALREADY_BOUND("USER_EMAIL_ALREADY_BOUND", "该邮箱已被其他账号绑定"),
    /** 新邮箱与当前绑定邮箱一致。 */
    EMAIL_UNCHANGED("USER_EMAIL_UNCHANGED", "该邮箱已经绑定当前账号"),
    /** 账号状态或邮箱在请求期间变化，未完成本次更新。 */
    EMAIL_BIND_NOT_COMPLETED("USER_EMAIL_BIND_NOT_COMPLETED", "邮箱绑定未完成，请重试"),
    PASSWORD_OLD_REQUIRED("USER_PASSWORD_OLD_REQUIRED", "请提供当前密码"),
    PASSWORD_OLD_INVALID("USER_PASSWORD_OLD_INVALID", "当前密码错误"),
    PASSWORD_UPDATE_NOT_COMPLETED("USER_PASSWORD_UPDATE_NOT_COMPLETED", "密码更新未完成，请重试"),
    /** 基础资料不存在或写入未完成，不误报为登录密码错误。 */
    PROFILE_UPDATE_NOT_COMPLETED("USER_PROFILE_UPDATE_NOT_COMPLETED", "个人资料更新未完成，请重试"),
    /** 文件不存在、未确认、归属不符或用途不符。 */
    AVATAR_FILE_NOT_READY("USER_AVATAR_FILE_NOT_READY", "头像文件尚未确认或无权使用"),
    /** 文件服务无法完成安全核验，不能跳过校验保存头像。 */
    AVATAR_SERVICE_UNAVAILABLE("USER_AVATAR_SERVICE_UNAVAILABLE", "头像服务暂时不可用，请稍后重试"),
    /** 未配置可用默认头像，不能随机分配或展示目录。 */
    DEFAULT_AVATAR_UNAVAILABLE("USER_DEFAULT_AVATAR_UNAVAILABLE", "默认头像暂时不可用，请稍后重试"),
    /** 只能选择管理员配置的默认头像。 */
    DEFAULT_AVATAR_INVALID("USER_DEFAULT_AVATAR_INVALID", "请选择列表中的默认头像");

    private final String code;
    private final String message;

    UserCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
