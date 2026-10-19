package org.ninestick.codekin.file.domain.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * 统一上传签名申请；用户身份来自网关上下文，不接收用户 ID 或对象路径。
 *
 * @author ninestick
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadSignatureDto {
    /** UploadType 枚举的 code，例如 AVATAR、RESUME_PHOTO；各用途独立限制和限流。 */
    @NotBlank @Size(max = 32) private String typeCode;
    /** 原始文件名，仅用于提取扩展名，不用于生成对象路径。 */
    @NotBlank @Size(max = 200) private String fileName;
    /** 客户端读取的文件字节数，业务最大值再由枚举检查。 */
    @NotNull @Positive private Long size;
    /** 声明的 MIME，签名策略会锁定此值。 */
    @NotBlank @Size(max = 100) private String contentType;
}
