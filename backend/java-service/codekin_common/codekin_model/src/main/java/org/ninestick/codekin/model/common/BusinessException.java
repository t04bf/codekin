package org.ninestick.codekin.model.common;

import lombok.Getter;
import org.slf4j.helpers.MessageFormatter;

/**
 * 跨业务模块复用的业务异常，仅保留稳定错误码。
 *
 * @author ninestick
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 由业务域枚举提供的错误码。 */
    private final Recode recode;

    public BusinessException(Recode recode) {
        super(recode.getMessage());
        this.recode = recode;
    }

    /** 使用自定义消息模板；占位符按 SLF4J 的 {} 规则替换。 */
    public BusinessException(Recode recode, String messageTemplate, Object... args) {
        super(MessageFormatter.arrayFormat(messageTemplate, args).getMessage());
        this.recode = recode;
    }
}
