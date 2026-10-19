package org.ninestick.codekin.file.domain.vo;

import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/**
 * 前端可直接用于 multipart/form-data POST 的短期凭证，不包含 Secret Key。
 *
 * @author ninestick
 */
@Getter
@Builder
public class UploadSignatureVo {
    /** 后端上传记录 ID；确认时只提交此 ID。 */
    private final String fileId;
    /** 浏览器直传地址。 */
    private final String uploadUrl;
    /** HTTP 方法，当前为 POST。 */
    private final String method;
    /** SDK 签发的所有表单字段，前端须原样提交并在最后添加 file。 */
    private final Map<String, String> fields;
    /** 签名失效时间。 */
    private final Instant expiresAt;
    /** 可确认上传的截止时间。 */
    private final Instant confirmationExpiresAt;
}

