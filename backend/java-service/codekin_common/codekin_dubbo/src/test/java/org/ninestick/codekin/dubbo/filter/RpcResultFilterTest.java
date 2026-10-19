package org.ninestick.codekin.dubbo.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.CompletableFuture;
import org.apache.dubbo.rpc.*;
import org.junit.jupiter.api.*;
import org.ninestick.codekin.model.common.*;

/**
 * 覆盖 Dubbo 同步异常、延迟完成、响应异常、无效返回与基础设施调用失败。
 *
 * @author ninestick
 */
class RpcResultFilterTest {
    /** 提供方的实际过滤器。 */
    private final RpcResultProviderFilter provider = new RpcResultProviderFilter();
    /** 消费方集群出口。 */
    private final RpcResultConsumerFilter consumer = new RpcResultConsumerFilter();
    /** 当前调用的下一个节点。 */
    private Invoker<TestApi> invoker;
    /** 返回 RpcResult 的方法元数据。 */
    private RpcInvocation invocation;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        invoker = mock(Invoker.class);
        when(invoker.getInterface()).thenReturn(TestApi.class);
        invocation = new RpcInvocation(TestApi.class.getMethod("query"), TestApi.class.getName(),
                TestApi.class.getName(), new Object[0]);
        invocation.setInvoker(invoker);
    }

    @Test
    void successfulAndBusinessFailureResultsAreNotWrappedAgain() throws Exception {
        var value = RpcResult.success("ok");
        var response = new AppResponse(value);
        response.setAttachment("trace-id", "test-trace");
        when(invoker.invoke(invocation)).thenReturn(AsyncRpcResult.newDefaultAsyncResult(response, invocation));
        Result result = provider.invoke(invoker, invocation).get();
        assertSame(value, result.getValue());
        assertEquals("test-trace", result.getAttachment("trace-id"));
        var failure = RpcResult.failure(CommonCode.NOT_FOUND, "没有对应数据");
        when(invoker.invoke(invocation)).thenReturn(new AppResponse(failure));
        assertSame(failure, provider.invoke(invoker, invocation).get().getValue());
    }

    @Test
    void synchronousBusinessExceptionPreservesCodeAndFormattedMessage() throws Exception {
        when(invoker.invoke(invocation)).thenThrow(new BusinessException(CommonCode.NOT_FOUND, "文件 {} 不存在", 99));
        Result result = provider.invoke(invoker, invocation).get();
        assertFalse(result.hasException());
        var value = (RpcResult<?>) result.getValue();
        assertEquals(CommonCode.NOT_FOUND.getCode(), value.getCode());
        assertEquals("文件 99 不存在", value.getMessage());
        assertNull(value.getData());
    }

    @Test
    void responseExceptionIsConvertedBeforeSerialization() throws Exception {
        when(invoker.invoke(invocation)).thenReturn(AsyncRpcResult.newDefaultAsyncResult(
                new BusinessException(CommonCode.INVALID_ARGUMENT, "参数 {} 不正确", "fileId"), invocation));
        Result response = provider.invoke(invoker, invocation).get();
        assertFalse(response.hasException());
        assertEquals("参数 fileId 不正确", ((RpcResult<?>) response.getValue()).getMessage());
    }

    @Test
    void delayedCompletionAndExceptionalFutureDoNotEscape() throws Exception {
        var future = new CompletableFuture<AppResponse>();
        when(invoker.invoke(invocation)).thenReturn(new AsyncRpcResult(future, invocation));
        Result result = provider.invoke(invoker, invocation);
        assertFalse(((AsyncRpcResult) result).getResponseFuture().isDone());
        future.completeExceptionally(new java.util.concurrent.CompletionException(
                new BusinessException(CommonCode.NOT_FOUND, "远端没有该文件")));
        assertEquals("远端没有该文件", ((RpcResult<?>) result.get().getValue()).getMessage());
    }

    @Test
    void consumerConvertsTimeoutAndUnavailableProvider() throws Exception {
        when(invoker.invoke(invocation)).thenThrow(new RpcException(RpcException.NETWORK_EXCEPTION, "private-address"));
        var unavailable = (RpcResult<?>) consumer.invoke(invoker, invocation).get().getValue();
        assertEquals(RpcCode.CALL_FAILED.getCode(), unavailable.getCode());
        assertFalse(unavailable.getMessage().contains("private-address"));
        var future = new CompletableFuture<AppResponse>();
        doReturn(new AsyncRpcResult(future, invocation)).when(invoker).invoke(invocation);
        var result = consumer.invoke(invoker, invocation);
        future.completeExceptionally(new RpcException(RpcException.TIMEOUT_EXCEPTION, "private-timeout-details"));
        assertEquals(RpcCode.CALL_FAILED.getCode(), ((RpcResult<?>) result.get().getValue()).getCode());
    }

    @Test
    void unknownExceptionsDoNotLeakTheirMessages() throws Exception {
        when(invoker.invoke(invocation)).thenThrow(new IllegalStateException("password=private-value"));
        var result = (RpcResult<?>) provider.invoke(invoker, invocation).get().getValue();
        assertEquals(RpcCode.INTERNAL_ERROR.getCode(), result.getCode());
        assertTrue(result.getMessage().contains("IllegalStateException"));
        assertFalse(result.getMessage().contains("private-value"));
        assertNull(result.getData());
    }

    @Test
    void nullAndMalformedResultsCannotMasqueradeAsSuccess() throws Exception {
        when(invoker.invoke(invocation)).thenReturn(new AppResponse((Object) null));
        assertEquals(RpcCode.INVALID_RESPONSE.getCode(), ((RpcResult<?>) provider.invoke(invoker, invocation).get().getValue()).getCode());
        when(invoker.invoke(invocation)).thenReturn(new AppResponse(RpcResult.builder().message("缺少结果码").build()));
        assertEquals(RpcCode.INVALID_RESPONSE.getCode(), ((RpcResult<?>) provider.invoke(invoker, invocation).get().getValue()).getCode());
    }

    @Test
    void frameworkMethodsKeepTheirOriginalProtocolAndFatalErrorsPropagate() throws Exception {
        RpcInvocation legacy = new RpcInvocation(TestApi.class.getMethod("frameworkMetadata"),
                TestApi.class.getName(), TestApi.class.getName(), new Object[0]);
        legacy.setInvoker(invoker);
        var response = new AppResponse("metadata");
        when(invoker.invoke(legacy)).thenReturn(response);
        assertSame(response, provider.invoke(invoker, legacy));
        when(invoker.invoke(invocation)).thenThrow(new LinkageError("test-fatal"));
        assertThrows(LinkageError.class, () -> provider.invoke(invoker, invocation));
    }

    /** 同时模拟业务方法和不属于项目业务契约的框架元数据方法。 */
    interface TestApi {
        RpcResult<String> query();
        String frameworkMetadata();
    }
}
