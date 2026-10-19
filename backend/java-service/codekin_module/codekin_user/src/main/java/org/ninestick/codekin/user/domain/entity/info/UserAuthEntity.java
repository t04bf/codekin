package org.ninestick.codekin.user.domain.entity.info;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用户认证数据；主键同时是全平台用户 ID。
 *
 * @author ninestick
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_auth")
public class UserAuthEntity {

    /** MyBatis-Plus 生成的雪花 ID。 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 账号状态编码，对应 UserAccountStatus。 */
    private Byte status;

    /** 最近一次成功登录的客户端 IP。 */
    private String lastLoginIp;

    /** 最近一次成功登录时间，使用 UTC。 */
    private LocalDateTime lastLoginAt;

    /** 统一格式且包含国家/地区码的手机号。 */
    private String phone;

    /** 手机号验证时间，未验证时不得用于登录。 */
    private LocalDateTime phoneVerifiedAt;

    /** 统一小写的邮箱。 */
    private String email;

    /** 邮箱验证时间，未验证时不得用于登录。 */
    private LocalDateTime emailVerifiedAt;

    /** 带算法标识的密码散列，绝不存储明文。 */
    private String passwordHash;

    /** 最近设置或修改密码的时间。 */
    private LocalDateTime passwordUpdatedAt;

    /** 创建时间，使用 UTC。 */
    private LocalDateTime createdAt;

    /** 更新时间，使用 UTC。 */
    private LocalDateTime updatedAt;
}
