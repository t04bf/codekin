package org.ninestick.codekin.user.domain.dto.info;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ninestick.codekin.user.domain.enums.UserGender;

/**
 * 替换当前用户的可编辑基础资料；选填字段为 NULL 时清空原值。
 * 不接收用户 ID、头像对象标识或认证字段，防止越权及绕过绑定验证。
 *
 * @author ninestick
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUpdateDto {
    /** 公开展示昵称，与数据库字段长度保持一致。 */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 40, message = "昵称不能超过 40 个字符")
    private String nickname;

    /** 公开个人简介，可清空。 */
    @Size(max = 500, message = "个人简介不能超过 500 个字符")
    private String bio;

    /** 性别枚举，未填写传 NULL。 */
    private UserGender genderCode;

    /** 生日只接受今天或更早的日期，默认仅本人可见。 */
    @PastOrPresent(message = "生日不能晚于今天")
    private LocalDate birthDate;

    /** 用户自填真实姓名，不代表实名验证且不会公开展示。 */
    @Size(max = 100, message = "真实姓名不能超过 100 个字符")
    private String realName;
}
