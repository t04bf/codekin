package org.ninestick.codekin.model.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 验证自定义消息不会改变业务错误码。
 *
 * @author ninestick
 */
class BusinessExceptionTest {

    @Test
    void customTemplateKeepsRecode() {
        BusinessException exception = new BusinessException(CommonCode.INVALID_ARGUMENT, "字段 {} 不合法", "email");
        R<Void> result = R.failure(exception.getRecode(), exception.getMessage());

        assertEquals("COMMON_INVALID_ARGUMENT", result.getCode());
        assertEquals("字段 email 不合法", result.getMessage());
    }
}
