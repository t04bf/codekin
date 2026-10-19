package org.ninestick.codekin.webmvc;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.model.common.CommonCode;
import org.ninestick.codekin.redis.RedisService;
import org.ninestick.codekin.security.AuthEndpointPolicy;
import org.ninestick.codekin.security.GatewayIdentityService;
import org.ninestick.codekin.security.GatewaySignedIdentity;
import org.ninestick.codekin.security.UserContextHolder;
import org.ninestick.codekin.security.VerifiedGatewayRequest;
import org.ninestick.codekin.security.constants.AuthConstants;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * 业务服务统一验签网关身份头，并在请求作用域内维护用户上下文。
 *
 * @author ninestick
 */
public class GatewayIdentityInterceptor implements AsyncHandlerInterceptor {
    /** 跨业务服务共享的随机数消费前缀，防止签名身份头重放。 */
    private static final String NONCE_PREFIX = "codekin:gateway:nonce:";

    /** 公开路径规则。 */
    private final AuthEndpointPolicy endpointPolicy;
    /** 网关身份头 HMAC 校验。 */
    private final GatewayIdentityService identityService;
    /** 分布式一次性随机数登记。 */
    private final RedisService redisService;

    public GatewayIdentityInterceptor(AuthEndpointPolicy endpointPolicy,
            GatewayIdentityService identityService, RedisService redisService) {
        this.endpointPolicy = endpointPolicy;
        this.identityService = identityService;
        this.redisService = redisService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Tomcat 线程会复用，首先清掉任何未正常清理的旧身份。
        UserContextHolder.clear();
        List<String> names = List.of(AuthConstants.GATEWAY_USER_ID_HEADER,
                AuthConstants.GATEWAY_SESSION_ID_HEADER,
                AuthConstants.GATEWAY_TIMESTAMP_HEADER,
                AuthConstants.GATEWAY_NONCE_HEADER,
                AuthConstants.GATEWAY_SIGNATURE_HEADER,
                AuthConstants.GATEWAY_CLIENT_ADDRESS_HEADER);
        for (String name : names) {
            if (Collections.list(request.getHeaders(name)).size() != 1) {
                throw new BusinessException(CommonCode.UNAUTHORIZED);
            }
        }
        GatewaySignedIdentity signed = GatewaySignedIdentity.builder()
                .userId(request.getHeader(AuthConstants.GATEWAY_USER_ID_HEADER))
                .sessionId(request.getHeader(AuthConstants.GATEWAY_SESSION_ID_HEADER))
                .timestamp(request.getHeader(AuthConstants.GATEWAY_TIMESTAMP_HEADER))
                .nonce(request.getHeader(AuthConstants.GATEWAY_NONCE_HEADER))
                .signature(request.getHeader(AuthConstants.GATEWAY_SIGNATURE_HEADER))
                .clientAddress(request.getHeader(AuthConstants.GATEWAY_CLIENT_ADDRESS_HEADER))
                .build();
        String target = request.getRequestURI()
                + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        VerifiedGatewayRequest verified = identityService.verify(signed, request.getMethod(), target);
        if (verified.getUser() == null && !endpointPolicy.isPublic(request.getMethod(), request.getRequestURI())) {
            throw new BusinessException(CommonCode.UNAUTHORIZED);
        }
        if (!redisService.setIfAbsent(NONCE_PREFIX + signed.getNonce(), "1",
                identityService.getMaxAge().plus(Duration.ofSeconds(5)))) {
            throw new BusinessException(CommonCode.UNAUTHORIZED);
        }
        UserContextHolder.setClientIp(verified.getClientAddress());
        if (verified.getUser() != null) {
            UserContextHolder.set(verified.getUser());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception exception) {
        UserContextHolder.clear();
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response,
            Object handler) {
        UserContextHolder.clear();
    }
}
