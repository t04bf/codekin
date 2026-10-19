package org.ninestick.codekin.dubbo.api.file;

/**
 * 文件服务提供方与消费方共享的 RPC 契约标识。
 *
 * @author ninestick
 */
public final class FileRpcConstants {
    /** 当前项目的服务分组。 */
    public static final String GROUP = "codekin";
    /** RpcResult 结果契约，与原始 DTO 返回的 1.0 隔离。 */
    public static final String VERSION = "2.0";

    private FileRpcConstants() { }
}
