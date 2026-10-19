package org.ninestick.codekin.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.model.common.CommonCode;
import org.ninestick.codekin.security.AuthEndpointPolicy;
import org.ninestick.codekin.security.GatewayContextProperties;
import org.ninestick.codekin.security.GatewayIdentityService;
import org.ninestick.codekin.security.GatewaySignedIdentity;
import org.ninestick.codekin.security.JwtSessionService;
import org.ninestick.codekin.security.constants.AuthConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * WebFlux 网关清除伪造身份头，并按统一业务格式拒绝未认证请求。
 *
 * @author ninestick
 */
class GatewayAuthenticationFilterTest {
    @Test
    void publicRequestReplacesForgedHeadersWithSignedAnonymousIdentity() {
        GatewayContextProperties properties = new GatewayContextProperties();
        properties.setSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        properties.setPublicEndpoints(List.of("POST /api/auth/sms/login/send"));
        GatewayIdentityService signer = new GatewayIdentityService(properties);
        GatewayAuthenticationFilter filter = new GatewayAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "identityService", signer);
        ReflectionTestUtils.setField(filter, "endpointPolicy", new AuthEndpointPolicy(properties));
        ReflectionTestUtils.setField(filter, "jwtSessionService", mock(JwtSessionService.class));
        ReflectionTestUtils.setField(filter, "clientIpResolver",
                new GatewayClientIpResolver(new GatewayClientIpProperties()));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/auth/sms/login/send")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8088))
                .header(HttpHeaders.AUTHORIZATION, "Bearer forged")
                .header(GatewayClientIpResolver.FORWARDED_FOR_HEADER, "198.51.100.9")
                .header(AuthConstants.GATEWAY_USER_ID_HEADER, "999")
                .header(AuthConstants.GATEWAY_SIGNATURE_HEADER, "forged"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = next -> {
            forwarded.set(next);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertNull(headers.getFirst(HttpHeaders.AUTHORIZATION));
        assertNull(headers.getFirst(GatewayClientIpResolver.FORWARDED_FOR_HEADER));
        assertEquals("", headers.getFirst(AuthConstants.GATEWAY_USER_ID_HEADER));
        assertNotEquals("forged", headers.getFirst(AuthConstants.GATEWAY_SIGNATURE_HEADER));
        GatewaySignedIdentity signed = GatewaySignedIdentity.builder()
                .userId(headers.getFirst(AuthConstants.GATEWAY_USER_ID_HEADER))
                .sessionId(headers.getFirst(AuthConstants.GATEWAY_SESSION_ID_HEADER))
                .timestamp(headers.getFirst(AuthConstants.GATEWAY_TIMESTAMP_HEADER))
                .nonce(headers.getFirst(AuthConstants.GATEWAY_NONCE_HEADER))
                .signature(headers.getFirst(AuthConstants.GATEWAY_SIGNATURE_HEADER))
                .clientAddress(headers.getFirst(AuthConstants.GATEWAY_CLIENT_ADDRESS_HEADER))
                .build();
        assertEquals("127.0.0.1", signer.verify(signed, "POST", "/api/auth/sms/login/send")
                .getClientAddress());
        assertNull(signer.verify(signed, "POST", "/api/auth/sms/login/send").getUser());
    }

    @Test
    void protectedRequestWithoutTokenReturnsBusinessError() {
        GatewayContextProperties properties = new GatewayContextProperties();
        properties.setSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        GatewayAuthenticationFilter filter = new GatewayAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "identityService", new GatewayIdentityService(properties));
        ReflectionTestUtils.setField(filter, "endpointPolicy", new AuthEndpointPolicy(properties));
        ReflectionTestUtils.setField(filter, "clientIpResolver",
                new GatewayClientIpResolver(new GatewayClientIpProperties()));
        JwtSessionService sessions = mock(JwtSessionService.class);
        when(sessions.requireSession(null)).thenThrow(new BusinessException(CommonCode.UNAUTHORIZED));
        ReflectionTestUtils.setField(filter, "jwtSessionService", sessions);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/users/me").remoteAddress(new InetSocketAddress("127.0.0.1", 8088)));

        filter.filter(exchange, next -> Mono.empty()).block();

        assertEquals(200, exchange.getResponse().getStatusCode().value());
        assertEquals("application/json", exchange.getResponse().getHeaders().getContentType().toString());
        assertTrue(exchange.getResponse().getBodyAsString().block().contains("COMMON_UNAUTHORIZED"));
    }

    @Test
    void trustedProxyClientIpIsSignedAndForwardedHeaderIsRemoved() {
        GatewayContextProperties security = new GatewayContextProperties();
        security.setSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        security.setPublicEndpoints(List.of("POST /api/auth/sms/login/send"));
        GatewayClientIpProperties ip = new GatewayClientIpProperties();
        ip.setMode(GatewayClientIpProperties.Mode.TRUSTED_PROXY);
        ip.setTrustedProxies(List.of("10.0.0.0/24"));
        GatewayAuthenticationFilter filter = new GatewayAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "identityService", new GatewayIdentityService(security));
        ReflectionTestUtils.setField(filter, "endpointPolicy", new AuthEndpointPolicy(security));
        ReflectionTestUtils.setField(filter, "jwtSessionService", mock(JwtSessionService.class));
        ReflectionTestUtils.setField(filter, "clientIpResolver", new GatewayClientIpResolver(ip));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/auth/sms/login/send")
                .remoteAddress(new InetSocketAddress("10.0.0.8", 8088))
                .header(GatewayClientIpResolver.FORWARDED_FOR_HEADER, "203.0.113.200, 198.51.100.5"));
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertEquals("198.51.100.5", headers.getFirst(AuthConstants.GATEWAY_CLIENT_ADDRESS_HEADER));
        assertNull(headers.getFirst(GatewayClientIpResolver.FORWARDED_FOR_HEADER));
    }
}
