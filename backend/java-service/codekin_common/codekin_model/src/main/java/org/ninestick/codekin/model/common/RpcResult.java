package org.ninestick.codekin.model.common;

import java.io.Serializable;
import java.util.Objects;
import lombok.*;
import org.slf4j.helpers.MessageFormatter;

/**
 * 所有业务 RPC 接口的统一结果；不携带异常对象或堆栈。
 *
 * @author ninestick
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RpcResult<T> implements Serializable {
    /** RPC 序列化版本。 */
    private static final long serialVersionUID = 1L;
    /** 机器可读结果码，成功使用公共 SUCCESS 编码。 */
    private String code;
    /** 成功说明或经过处理的错误消息。 */
    private String message;
    /** 成功数据；失败时不返回部分业务数据。 */
    private T data;

    /** 成功也允许没有业务数据，例如 RpcResult<Void>。 */
    public static <T> RpcResult<T> success(T data) {
        return RpcResult.<T>builder().code(CommonCode.SUCCESS.getCode())
                .message(CommonCode.SUCCESS.getMessage()).data(data).build();
    }

    /** 沿用各业务域的 Recode，不要求消费方依赖提供方的枚举类。 */
    public static <T> RpcResult<T> failure(Recode recode) {
        Objects.requireNonNull(recode, "RPC 错误码不能为空");
        return RpcResult.<T>builder().code(recode.getCode()).message(recode.getMessage()).build();
    }

    /** 自定义消息沿用 SLF4J 的 {} 占位符，支持业务异常已格式化的消息。 */
    public static <T> RpcResult<T> failure(Recode recode, String messageTemplate, Object... args) {
        Objects.requireNonNull(recode, "RPC 错误码不能为空");
        return RpcResult.<T>builder().code(recode.getCode())
                .message(MessageFormatter.arrayFormat(Objects.requireNonNull(messageTemplate), args).getMessage()).build();
    }

    /** 调用方必须先检查结果，再使用 data；不能把失败结果当作空查询结果。 */
    public boolean isSuccess() {
        return CommonCode.SUCCESS.getCode().equals(code);
    }
}
