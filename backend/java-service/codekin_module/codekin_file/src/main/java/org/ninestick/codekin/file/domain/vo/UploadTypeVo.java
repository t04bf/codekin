package org.ninestick.codekin.file.domain.vo;

import java.util.List;
import java.util.Set;
import lombok.*;

/**
 * 将枚举限制返回给前端，避免两端独立维护文件白名单。
 *
 * @author ninestick
 */
@Getter
@Builder
public class UploadTypeVo {
    /** 文件用途编码。 */
    private final String code;
    /** 最大字节数。 */
    private final long maxBytes;
    /** 允许扩展名。 */
    private final Set<String> extensions;
    /** 允许 MIME。 */
    private final List<String> contentTypes;
    /** 最大宽度。 */
    private final int maxWidth;
    /** 最大高度。 */
    private final int maxHeight;
    /** 最大像素数。 */
    private final long maxPixels;
}

