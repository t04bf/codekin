package org.ninestick.codekin.file.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 上传记录状态；数据库与业务条件均使用枚举编码。
 *
 * @author ninestick
 */
@Getter
@RequiredArgsConstructor
public enum FileStatus {
    /** 已签发，尚未确认。 */
    UPLOADING("UPLOADING"),
    /** 已占用确认处理，禁止并发解码。 */
    PROCESSING("PROCESSING"),
    /** 核验及重新编码完成，正式 key 可被业务引用。 */
    READY("READY"),
    /** 核验失败，不允许引用。 */
    REJECTED("REJECTED"),
    /** 临时文件已清理。 */
    EXPIRED("EXPIRED");
    /** 持久化状态编码。 */
    private final String code;
}

