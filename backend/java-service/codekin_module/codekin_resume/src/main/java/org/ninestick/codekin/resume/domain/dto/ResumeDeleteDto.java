package org.ninestick.codekin.resume.domain.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/** 删除前核对读取到的版本，避免删除其他页面的新修改。 @author ninestick */
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ResumeDeleteDto {
    /** 简历当前版本，通过查询参数传递。 */
    @NotBlank @Pattern(regexp = "^[1-9][0-9]{0,18}$") @DecimalMax("9223372036854775806")
    private String expectedVersion;
}
