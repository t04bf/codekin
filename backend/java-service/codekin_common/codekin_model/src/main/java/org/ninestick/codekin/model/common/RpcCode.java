package org.ninestick.codekin.model.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * RPC 基础设施错误，与提供方业务错误码区分。
 *
 * @author ninestick
 */
@Getter
@RequiredArgsConstructor
public enum RpcCode implements Recode {
    /** 提供方发生未知执行异常。 */
    INTERNAL_ERROR("RPC_INTERNAL_ERROR", "RPC 服务执行异常，请稍后重试"),
    /** 消费方调用失败，包括超时、服务不可达和无可用实例。 */
    CALL_FAILED("RPC_CALL_FAILED", "RPC 服务调用失败，请稍后重试"),
    /** 接口返回 null 或缺少有效结果码，不能视为成功。 */
    INVALID_RESPONSE("RPC_INVALID_RESPONSE", "RPC 服务返回了无效结果");

    /** 稳定结果码。 */
    private final String code;
    /** 不暴露底层请求内容的错误说明。 */
    private final String message;
}
