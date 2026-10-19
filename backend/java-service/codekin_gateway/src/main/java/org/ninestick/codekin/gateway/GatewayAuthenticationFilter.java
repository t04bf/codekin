package org.ninestick.codekin.gateway;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.model.common.CommonCode;
import org.ninestick.codekin.model.common.R;
import org.ninestick.codekin.security.AuthEndpointPolicy;
import org.ninestick.codekin.security.GatewayIdentityService;
import org.ninestick.codekin.security.GatewaySignedIdentity;
import org.ninestick.codekin.security.JwtSessionService;
import org.ninestick.codekin.security.constants.AuthConstants;
import org.ninestick.codekin.utils.JsonUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 在 WebFlux 网关校验 JWT 和 Redis 会话，并为业务服务签发一次性身份头。
 *
 * @author ninestick
 */
@Slf4j
@Component
public class GatewayAuthenticationFilter implements GlobalFilter, Ordered {
    /** JWT 会话校验会访问同步 Redis，因此在 boundedElastic 线程执行。 */
    @Resource
    private JwtSessionService jwtSessionService;
    /** 公开接口的统一匹配规则。 */
    @Resource
    private AuthEndpointPolicy endpointPolicy;
    /** 与业务服务共享的短时身份头签名器。 */
    @Resource
    private GatewayIdentityService identityService;
    /** 按 Nacos 配置从连接对端或可信代理链解析客户端 IP。 */
    @Resource
    private GatewayClientIpResolver clientIpResolver;

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        String path = request.getURI().getRawPath();
        String query = request.getURI().getRawQuery();
        String target = path + (query == null ? "" : "?" + query);
        String clientAddress;
        try {
            clientAddress = clientIpResolver.resolve(request);
        } catch (RuntimeException exception) {
            log.warn("网关无法确定客户端 IP exceptionType={}", exception.getClass().getSimpleName());
            return writeError(exchange, CommonCode.INTERNAL_ERROR, CommonCode.INTERNAL_ERROR.getMessage());
        }
        Mono<GatewaySignedIdentity> identity = endpointPolicy.isPublic(method, path)
                ? Mono.fromSupplier(() -> identityService.sign(null, clientAddress, method, target))
                : Mono.fromCallable(() -> identityService.sign(
                        jwtSessionService.requireSession(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)),
                        clientAddress, method, target))
                        .subscribeOn(Schedulers.boundedElastic());

        return identity.flatMap(signed -> {
            // 丢弃客户端伪造的内部头；业务服务只接收本次网关签发的值。
            ServerHttpRequest forwarded = request.mutate().headers(headers -> {
                headers.remove(HttpHeaders.AUTHORIZATION);
                headers.remove(AuthConstants.GATEWAY_USER_ID_HEADER);
                headers.remove(AuthConstants.GATEWAY_SESSION_ID_HEADER);
                headers.remove(AuthConstants.GATEWAY_TIMESTAMP_HEADER);
                headers.remove(AuthConstants.GATEWAY_NONCE_HEADER);
                headers.remove(AuthConstants.GATEWAY_SIGNATURE_HEADER);
                headers.remove(AuthConstants.GATEWAY_CLIENT_ADDRESS_HEADER);
                headers.remove(GatewayClientIpResolver.FORWARDED_FOR_HEADER);
                headers.remove(GatewayClientIpResolver.FORWARDED_HEADER);
                headers.remove(GatewayClientIpResolver.REAL_IP_HEADER);
                headers.set(AuthConstants.GATEWAY_USER_ID_HEADER, signed.getUserId());
                headers.set(AuthConstants.GATEWAY_SESSION_ID_HEADER, signed.getSessionId());
                headers.set(AuthConstants.GATEWAY_TIMESTAMP_HEADER, signed.getTimestamp());
                headers.set(AuthConstants.GATEWAY_NONCE_HEADER, signed.getNonce());
                headers.set(AuthConstants.GATEWAY_SIGNATURE_HEADER, signed.getSignature());
                headers.set(AuthConstants.GATEWAY_CLIENT_ADDRESS_HEADER, signed.getClientAddress());
            }).build();
            return chain.filter(exchange.mutate().request(forwarded).build());
        }).onErrorResume(BusinessException.class,
                exception -> writeError(exchange, exception.getRecode(), exception.getMessage()))
                .onErrorResume(Exception.class, exception -> {
                    log.error("网关鉴权或转发失败 exceptionType={}", exception.getClass().getSimpleName());
                    return writeError(exchange, CommonCode.INTERNAL_ERROR, CommonCode.INTERNAL_ERROR.getMessage());
                });
    }

    /** 网关遵循业务响应约定，错误详情由 R.code 而非 HTTP 状态码表示。 */
    private Mono<Void> writeError(ServerWebExchange exchange,
            org.ninestick.codekin.model.common.Recode code, String message) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(new IllegalStateException("网关响应已经提交"));
        }
        exchange.getResponse().setRawStatusCode(200);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().setCacheControl("no-store");
        byte[] body = JsonUtils.toJson(R.failure(code, message)).getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
