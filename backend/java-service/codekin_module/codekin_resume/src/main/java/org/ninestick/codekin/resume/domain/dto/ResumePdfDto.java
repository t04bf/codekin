package org.ninestick.codekin.resume.domain.dto;

import jakarta.validation.constraints.*;
import java.util.Map;
import lombok.*;

/** 导出已确认的简历版本；图片只接收本次渲染的内存载荷。 @author ninestick */
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ResumePdfDto {
    @NotBlank @Pattern(regexp = "^[1-9][0-9]{0,18}$") @DecimalMax("9223372036854775806")
    private String expectedVersion;
    @NotNull @Size(max = 51)
    private Map<@Size(max = 120) String, @Size(max = 7000000) String> images;
}
