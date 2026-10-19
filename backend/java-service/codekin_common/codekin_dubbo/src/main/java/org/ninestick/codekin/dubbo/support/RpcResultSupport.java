package org.ninestick.codekin.dubbo.support;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.rpc.*;
import org.ninestick.codekin.model.common.*;

/**
 * Dubbo 两端共用的结果与异常转换，覆盖同步抛出和异步响应完成。
 *
 * @author ninestick
 */
@Slf4j
public final class RpcResultSupport {
    /** 仅处理声明返回 RpcResult 的业务方法，保留 Dubbo 元数据与框架接口的协议。 */
    public static Result invoke(Invoker<?> invoker, Invocation invocation, RpcCode fallbackCode) {
        try {
            // 接口声明是契约依据，避免依赖调用链尚未填充的 ServiceModel/返回类型元数据。
            if (invoker.getInterface().getMethod(invocation.getMethodName(), invocation.getParameterTypes())
                    .getReturnType() != RpcResult.class) return invoker.invoke(invocation);
        } catch (NoSuchMethodException exception) {
            // Dubbo 的 $echo、泛化等框架调用不属于业务接口声明。
            return invoker.invoke(invocation);
        }
        try {
            Result result = invoker.invoke(invocation);
            if (result instanceof AsyncRpcResult async) {
                // 不能在 invoke 返回时读取尚未完成的结果，也不能漏掉 Future 的异常完成。
                return new AsyncRpcResult(async.getResponseFuture().handle((response, error) -> error == null
                        ? completeResponse(response, invoker, invocation, fallbackCode)
                        : new AppResponse(failure(error, invoker, invocation, fallbackCode))), invocation);
            }
            AppResponse response = result == null ? null : new AppResponse(result.getValue());
            if (response != null) {
                response.setException(result.getException());
                response.setObjectAttachments(result.getObjectAttachments());
            }
            return AsyncRpcResult.newDefaultAsyncResult(completeResponse(response, invoker, invocation, fallbackCode), invocation);
        } catch (Exception exception) {
            return AsyncRpcResult.newDefaultAsyncResult(failure(exception, invoker, invocation, fallbackCode), invocation);
        }
    }

    /** 保留成功或业务失败结果；转换异常和不符合契约的空响应，不再次套 RpcResult。 */
    private static AppResponse completeResponse(AppResponse response, Invoker<?> invoker,
            Invocation invocation, RpcCode fallbackCode) {
        if (response == null) return new AppResponse(RpcResult.failure(RpcCode.INVALID_RESPONSE));
        if (response.hasException()) {
            RpcResult<?> result = failure(response.getException(), invoker, invocation, fallbackCode);
            response.setException(null);
            response.setValue(result);
        } else if (!(response.getValue() instanceof RpcResult<?> result)
                || result.getCode() == null || result.getCode().isBlank()) {
            log.error("RPC 返回值不符合契约 interface={} method={}", invoker.getInterface().getName(), invocation.getMethodName());
            response.setValue(RpcResult.failure(RpcCode.INVALID_RESPONSE));
        }
        return response;
    }

    /** 只返回业务消息或安全的基础设施说明，日志不打印调用参数、原始异常消息或凭证。 */
    private static RpcResult<?> failure(Throwable error, Invoker<?> invoker, Invocation invocation, RpcCode fallbackCode) {
        while ((error instanceof CompletionException || error instanceof ExecutionException
                || error instanceof InvocationTargetException) && error.getCause() != null) {
            error = error.getCause();
        }
        // JVM 致命错误不能伪装为可重试业务失败；普通 Exception 都转换为结果。
        if (error instanceof Error fatal) throw fatal;
        if (error instanceof BusinessException business) {
            log.warn("RPC 业务执行失败 interface={} method={} code={}", invoker.getInterface().getName(),
                    invocation.getMethodName(), business.getRecode().getCode());
            return RpcResult.failure(business.getRecode(), business.getMessage());
        }
        if (error instanceof IllegalArgumentException) {
            log.warn("RPC 参数不合法 interface={} method={}", invoker.getInterface().getName(), invocation.getMethodName());
            return RpcResult.failure(CommonCode.INVALID_ARGUMENT);
        }
        log.error("RPC 调用异常 interface={} method={} code={} exceptionType={}", invoker.getInterface().getName(),
                invocation.getMethodName(), fallbackCode.getCode(), error.getClass().getSimpleName());
        return RpcResult.failure(fallbackCode, "{}（{}）", fallbackCode.getMessage(), error.getClass().getSimpleName());
    }

    private RpcResultSupport() { }
}
