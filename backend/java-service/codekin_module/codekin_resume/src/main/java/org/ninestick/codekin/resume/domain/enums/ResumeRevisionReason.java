package org.ninestick.codekin.resume.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 本阶段只在创建或显式版本点保留快照。 @author ninestick */
@Getter @RequiredArgsConstructor
public enum ResumeRevisionReason {
    CREATE("CREATE"), MANUAL("MANUAL");
    private final String code;
}

