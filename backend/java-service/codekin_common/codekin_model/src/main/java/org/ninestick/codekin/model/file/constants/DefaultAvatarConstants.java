package org.ninestick.codekin.model.file.constants;

/**
 * 用户服务与文件服务共用的默认头像对象命名空间，不能用于签发任意对象读取权限。
 *
 * @author ninestick
 */
public final class DefaultAvatarConstants {
    /** 预置头像所在的固定目录，不属于用户直传创建的临时记录。 */
    public static final String OBJECT_KEY_PREFIX = "uploads/avatar/default/";
    /** 只允许该目录下的 PNG 文件名，排除 URL、子目录和路径穿越。 */
    public static final String OBJECT_KEY_PATTERN = OBJECT_KEY_PREFIX + "[A-Za-z0-9][A-Za-z0-9_-]*\\.png";
    /** 配置和一次 RPC 签名共同遵守的批量上限。 */
    public static final int MAX_BATCH_SIZE = 100;

    private DefaultAvatarConstants() { }
}
