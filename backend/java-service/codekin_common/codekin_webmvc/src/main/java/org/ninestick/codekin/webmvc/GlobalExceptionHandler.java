package org.ninestick.codekin.webmvc;

import lombok.extern.slf4j.Slf4j;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.model.common.CommonCode;
import org.ninestick.codekin.model.common.R;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Spring MVC 服务共用的异常出口，统一转换为 HTTP 200 和业务结果码。
 *
 * @author ninestick
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常记录错误码；不记录可能含有敏感信息的参数化消息。 */
    @ExceptionHandler(BusinessException.class)
    public R<Void> business(BusinessException exception) {
        log.warn("业务请求失败 code={}", exception.getRecode().getCode());
        return R.failure(exception.getRecode(), exception.getMessage());
    }

    /** 参数及请求体错误使用通用错误码。 */
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public R<Void> invalidArgument(Exception exception) {
        return R.failure(CommonCode.INVALID_ARGUMENT);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> notFound(NoResourceFoundException exception) {
        return R.failure(CommonCode.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> methodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return R.failure(CommonCode.METHOD_NOT_ALLOWED);
    }

    /** 意外异常仅记录到 SLF4J，响应不暴露内部细节。 */
    @ExceptionHandler(Exception.class)
    public R<Void> internalError(Exception exception) {
        log.error("请求处理失败", exception);
        return R.failure(CommonCode.INTERNAL_ERROR);
    }
}
