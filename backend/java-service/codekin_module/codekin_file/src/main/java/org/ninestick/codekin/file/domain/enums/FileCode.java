package org.ninestick.codekin.file.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ninestick.codekin.model.common.Recode;

/**
 * 文件服务业务错误，不暴露底层存储响应和签名内容。
 *
 * @author ninestick
 */
@Getter
@RequiredArgsConstructor
public enum FileCode implements Recode {
    /** 单实例图片处理预算已满。 */
    PROCESSING_BUSY("FILE_PROCESSING_BUSY", "图片处理繁忙，请稍后重试"),
    /** 未登记业务用途。 */
    INVALID_TYPE("FILE_INVALID_TYPE", "不支持的上传用途"),
    /** 扩展名或声明 MIME 不在业务白名单。 */
    INVALID_FORMAT("FILE_INVALID_FORMAT", "仅支持 JPEG、JPG、PNG、WebP 图片"),
    /** 实际文件大小超出业务限制。 */
    SIZE_LIMIT("FILE_SIZE_LIMIT", "文件大小不符合当前上传用途限制"),
    /** 文件内容不是可解码的白名单格式图片。 */
    INVALID_IMAGE("FILE_INVALID_IMAGE", "图片格式不正确或文件已损坏"),
    /** 解码之前检查到尺寸或像素数超限。 */
    PIXEL_LIMIT("FILE_PIXEL_LIMIT", "图片尺寸或总像素数超出当前上传用途限制"),
    /** 签名接口频率限制。 */
    RATE_LIMIT("FILE_RATE_LIMIT", "申请上传过于频繁，请稍后重试"),
    /** 不泄露文件是否属于其他用户。 */
    NOT_FOUND("FILE_NOT_FOUND", "文件不存在或无权访问"),
    /** 上传确认超过期限。 */
    EXPIRED("FILE_UPLOAD_EXPIRED", "上传已过期，请重新选择文件"),
    /** 上传尚未完成或确认已经在处理中。 */
    NOT_READY("FILE_NOT_READY", "文件尚未上传完成或正在处理中，请稍后重试"),
    /** 存储服务调用失败。 */
    STORAGE_UNAVAILABLE("FILE_STORAGE_UNAVAILABLE", "文件存储暂时不可用，请稍后重试");

    /** 稳定业务状态码。 */
    private final String code;
    /** 面向用户的说明。 */
    private final String message;
}

