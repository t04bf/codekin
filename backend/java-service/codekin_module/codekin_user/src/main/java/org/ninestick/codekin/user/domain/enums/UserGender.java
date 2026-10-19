package org.ninestick.codekin.user.domain.enums;

import lombok.Getter;

/**
 * 用户自填性别编码；未填写用数据库 NULL 表示，默认仅本人可见。
 *
 * @author ninestick
 */
@Getter
public enum UserGender {
    MALE("MALE"),
    FEMALE("FEMALE"),
    OTHER("OTHER");

    /** 资料表中的稳定编码。 */
    private final String code;

    UserGender(String code) {
        this.code = code;
    }
}
