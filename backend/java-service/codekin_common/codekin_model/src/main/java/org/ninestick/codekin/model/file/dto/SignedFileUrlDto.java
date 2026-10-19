package org.ninestick.codekin.model.file.dto;

import java.io.Serializable;
import java.time.Instant;
import lombok.*;

/** 私有文件的临时读取凭证，不用于数据库持久化。 @author ninestick */
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SignedFileUrlDto implements Serializable {
    /** 序列化版本。 */
    private static final long serialVersionUID = 1L;
    /** 临时 GET URL。 */
    private String url;
    /** 到期时间 UTC。 */
    private Instant expiresAt;
}
