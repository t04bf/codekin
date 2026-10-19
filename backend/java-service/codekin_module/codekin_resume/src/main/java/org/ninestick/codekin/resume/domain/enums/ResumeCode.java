package org.ninestick.codekin.resume.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ninestick.codekin.model.common.Recode;

/** 简历域错误；不存在、他人所有及已删除统一返回不可访问。 @author ninestick */
@Getter @RequiredArgsConstructor
public enum ResumeCode implements Recode {
    NOT_FOUND("RESUME_NOT_FOUND", "简历不存在或无权访问"),
    VERSION_CONFLICT("RESUME_VERSION_CONFLICT", "简历已在其他页面更新，请重新读取后保存"),
    MUTATION_REUSED("RESUME_MUTATION_REUSED", "保存请求标识已用于不同内容"),
    INVALID_DOCUMENT("RESUME_INVALID_DOCUMENT", "简历内容或排版格式不正确"),
    UNSUPPORTED_SCHEMA("RESUME_UNSUPPORTED_SCHEMA", "简历数据版本暂不支持"),
    TEMPLATE_UNAVAILABLE("RESUME_TEMPLATE_UNAVAILABLE", "该模板版本不可用于创建或切换"),
    PHOTO_NOT_READY("RESUME_PHOTO_NOT_READY", "证件照尚未确认或无权使用"),
    FILE_SERVICE_UNAVAILABLE("RESUME_FILE_SERVICE_UNAVAILABLE", "证件照服务暂时不可用，请稍后重试"),
    PDF_IMAGES_INVALID("RESUME_PDF_IMAGES_INVALID", "简历图片格式或大小不正确，请重新上传后导出"),
    PDF_RESOURCE_FAILED("RESUME_PDF_RESOURCE_FAILED", "简历字体或图片加载失败，请稍后重试"),
    PDF_BUSY("RESUME_PDF_BUSY", "当前导出较多，请稍后重试"),
    PDF_FAILED("RESUME_PDF_FAILED", "PDF 生成失败，请稍后重试");
    private final String code;
    private final String message;
}

