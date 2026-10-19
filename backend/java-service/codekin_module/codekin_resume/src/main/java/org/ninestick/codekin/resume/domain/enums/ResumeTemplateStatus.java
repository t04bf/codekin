package org.ninestick.codekin.resume.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 发布后的内容不原地修改；停用版本仍允许已有简历继续保存。 @author ninestick */
@Getter @RequiredArgsConstructor
public enum ResumeTemplateStatus {
    DRAFT("DRAFT"), PUBLISHED("PUBLISHED"), RETIRED("RETIRED");
    private final String code;
}

