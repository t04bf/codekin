package org.ninestick.codekin.model.file.enums;

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 文件业务用途及不可由前端放宽的上传限制；跨服务共用同一契约。
 *
 * @author ninestick
 */
@Getter
@RequiredArgsConstructor
public enum UploadType {
    /** 头像仅接收可解码图片，5 MiB，最大 4096 边长和 1200 万像素，输出最长边 512。 */
    AVATAR("AVATAR", 5L * 1024 * 1024,
            Map.of("jpeg", "image/jpeg", "jpg", "image/jpeg", "png", "image/png", "webp", "image/webp"),
            4096, 4096, 12_000_000L, 512, 10),
    /** 私有简历证件照沿用图片校验预算，最长边 1024，保持比例且不裁剪、不放大。 */
    RESUME_PHOTO("RESUME_PHOTO", 5L * 1024 * 1024,
            Map.of("jpeg", "image/jpeg", "jpg", "image/jpeg", "png", "image/png", "webp", "image/webp"),
            4096, 4096, 12_000_000L, 1024, 10);

    /** API 接收的稳定用途编码。 */
    private final String code;
    /** 最大源文件字节数，MiB 按 1024 进制计算。 */
    private final long maxBytes;
    /** 允许的扩展名及唯一对应的 MIME。 */
    private final Map<String, String> formats;
    /** 解码前允许的最大宽度。 */
    private final int maxWidth;
    /** 解码前允许的最大高度。 */
    private final int maxHeight;
    /** 解码前允许的最大总像素数。 */
    private final long maxPixels;
    /** 正式图片最长边，保持比例缩小，不放大。 */
    private final int outputMaxSide;
    /** 每个用户在配置的签名窗口内最多申请次数。 */
    private final int signatureLimit;

    /** 不硬编码默认用途；新增用途必须显式登记。 */
    public static UploadType fromCode(String code) {
        for (UploadType type : values()) {
            if (type.code.equals(code)) return type;
        }
        return null;
    }

    /** 返回前端可用于选文件过滤的扩展名。 */
    public Set<String> getExtensions() {
        return formats.keySet();
    }

    /** MIME 去重，避免 jpg/jpeg 重复。 */
    public List<String> getContentTypes() {
        return formats.values().stream().distinct().sorted().toList();
    }
}
