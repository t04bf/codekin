package org.ninestick.codekin.file.storage;

/**
 * S3 表单协议、正式图片格式和文件服务 Redis 键约定。
 *
 * @author ninestick
 */
public final class FileStorageConstants {
    /** 预签名表单上传方法。 */
    public static final String POST_METHOD = "POST";
    /** 临时区域，清理不影响正式文件。 */
    public static final String TEMPORARY_PREFIX = "uploads/";
    /** 已核验正式区域，不向前端签发写凭证。 */
    public static final String READY_PREFIX = "files/";
    /** 正式头像统一 PNG。 */
    public static final String PNG_CONTENT_TYPE = "image/png";
    /** ImageIO 输出格式。 */
    public static final String PNG_FORMAT = "png";
    /** 用户签名限流键前缀。 */
    public static final String SIGNATURE_RATE_PREFIX = "codekin:file:signature:";
    /** 签名条件名称。 */
    public static final String CONTENT_TYPE_FIELD = "Content-Type";
    /** POST 成功状态字段。 */
    public static final String SUCCESS_STATUS_FIELD = "success_action_status";
    /** 统一要求 POST 返回无内容成功状态。 */
    public static final String SUCCESS_STATUS = "204";
    /** 临时清理为进行中的确认留出五分钟缓冲。 */
    public static final int CLEANUP_GRACE_MINUTES = 5;

    private FileStorageConstants() { }
}

