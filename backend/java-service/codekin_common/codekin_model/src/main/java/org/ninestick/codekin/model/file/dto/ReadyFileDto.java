package org.ninestick.codekin.model.file.dto;

import java.io.Serializable;
import lombok.*;

/**
 * 已审核正式文件的服务间契约，不含上传凭证或存储密钥。
 *
 * @author ninestick
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadyFileDto implements Serializable {
    /** 序列化版本。 */
    private static final long serialVersionUID = 1L;
    /** 文件 ID，前端使用字符串避免雪花 ID 精度丢失。 */
    private String fileId;
    /** 文件所属用户。 */
    private Long ownerUserId;
    /** 已审核的业务用途编码。 */
    private String typeCode;
    /** 正式区的对象标识。 */
    private String objectKey;
    /** 实际正式文件字节数。 */
    private long size;
    /** 重新编码后的实际 MIME。 */
    private String contentType;
}

