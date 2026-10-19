package org.ninestick.codekin.security;

import com.alibaba.ttl.TransmittableThreadLocal;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.model.common.CommonCode;

/**
 * Servlet 请求线程中的用户身份；异步任务需显式使用 TTL 包装后再提交。
 *
 * @author ninestick
 */
public final class UserContextHolder {
    /** 请求结束必须清理，避免线程池复用时串入其他用户身份。 */
    private static final TransmittableThreadLocal<AuthenticatedUser> CONTEXT = new TransmittableThreadLocal<>();
    /** 公开接口也可使用网关已解析并签名的客户端 IP 执行发送频率限制。 */
    private static final TransmittableThreadLocal<String> CLIENT_IP = new TransmittableThreadLocal<>();

    private UserContextHolder() {
    }

    /** 仅由完成网关签名校验的拦截器设置。 */
    public static void set(AuthenticatedUser user) {
        CONTEXT.set(user);
    }

    /** 当前请求的身份，不存在时视为未认证。 */
    public static AuthenticatedUser requireUser() {
        AuthenticatedUser user = CONTEXT.get();
        if (user == null) {
            throw new BusinessException(CommonCode.UNAUTHORIZED);
        }
        return user;
    }

    public static Long requireUserId() {
        return requireUser().getUserId();
    }

    /** 只接收业务拦截器验签后的客户端 IP。 */
    public static void setClientIp(String clientIp) {
        CLIENT_IP.set(clientIp);
    }

    /** 从当前请求上下文读取网关签名的客户端 IP。 */
    public static String requireClientIp() {
        String clientIp = CLIENT_IP.get();
        if (clientIp == null) {
            throw new BusinessException(CommonCode.UNAUTHORIZED);
        }
        return clientIp;
    }

    /** 在请求正常结束、异常结束和异步切换时调用。 */
    public static void clear() {
        CONTEXT.remove();
        CLIENT_IP.remove();
    }
}
