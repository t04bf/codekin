package org.ninestick.codekin.model.common;

/**
 * 跨模块通用的业务错误码契约。
 *
 * @author ninestick
 */
public interface Recode {

    /** 稳定的机器可读错误码。 */
    String getCode();

    /** 面向调用方的错误说明。 */
    String getMessage();
}
