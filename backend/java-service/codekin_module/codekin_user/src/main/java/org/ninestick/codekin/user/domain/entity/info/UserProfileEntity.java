package org.ninestick.codekin.user.domain.entity.info;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用户基础资料，主键复用认证表 ID。
 *
 * @author ninestick
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_profile")
public class UserProfileEntity {

    /** 对应 user_auth.id。 */
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    /** 公开展示昵称。 */
    private String nickname;

    /** 对象存储中的头像标识。 */
    private String avatarKey;

    /** 可公开的个人简介。 */
    private String bio;

    /** 用户自填性别，默认不公开。 */
    private String genderCode;

    /** 用户自填生日，默认不公开。 */
    private LocalDate birthDate;

    /** 用户自填真实姓名，默认不公开且不代表实名验证。 */
    private String realName;

    /** 创建时间，使用 UTC。 */
    private LocalDateTime createdAt;

    /** 更新时间，使用 UTC。 */
    private LocalDateTime updatedAt;
}
