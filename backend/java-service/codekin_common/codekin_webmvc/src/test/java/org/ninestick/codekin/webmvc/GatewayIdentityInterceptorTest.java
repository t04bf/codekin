package org.ninestick.codekin.webmvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.redis.RedisService;
import org.ninestick.codekin.security.AuthEndpointPolicy;
import org.ninestick.codekin.security.AuthenticatedUser;
import org.ninestick.codekin.security.GatewayContextProperties;
import org.ninestick.codekin.security.GatewayIdentityService;
import org.ninestick.codekin.security.GatewaySignedIdentity;
import org.ninestick.codekin.security.UserContextHolder;
import org.ninestick.codekin.security.constants.AuthConstants;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 拦截器必须验证身份头并在请求完成后清理用户上下文。
 *
 * @author ninestick
 */
class GatewayIdentityInterceptorTest {
    @AfterEach
    void clear() {
        UserContextHolder.clear();
    }

    @Test
    void rejectsReplayAndClearsContext() {
        GatewayContextProperties properties = new GatewayContextProperties();
        properties.setSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        GatewayIdentityService service = new GatewayIdentityService(properties);
        RedisService redis = mock(RedisService.class);
        GatewayIdentityInterceptor interceptor = new GatewayIdentityInterceptor(
                new AuthEndpointPolicy(properties), service, redis);
        GatewaySignedIdentity signed = service.sign(
                AuthenticatedUser.builder().userId(123L).sessionId("jti").build(),
                "127.0.0.1", "GET", "/api/users/me");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader(AuthConstants.GATEWAY_USER_ID_HEADER, signed.getUserId());
        request.addHeader(AuthConstants.GATEWAY_SESSION_ID_HEADER, signed.getSessionId());
        request.addHeader(AuthConstants.GATEWAY_TIMESTAMP_HEADER, signed.getTimestamp());
        request.addHeader(AuthConstants.GATEWAY_NONCE_HEADER, signed.getNonce());
        request.addHeader(AuthConstants.GATEWAY_SIGNATURE_HEADER, signed.getSignature());
        request.addHeader(AuthConstants.GATEWAY_CLIENT_ADDRESS_HEADER, signed.getClientAddress());
        when(redis.setIfAbsent(any(), eq("1"), any())).thenReturn(true, false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        assertEquals(123L, UserContextHolder.requireUserId());
        assertEquals("127.0.0.1", UserContextHolder.requireClientIp());
        interceptor.afterCompletion(request, response, new Object(), null);
        assertThrows(BusinessException.class, UserContextHolder::requireUserId);
        assertThrows(BusinessException.class, UserContextHolder::requireClientIp);
        assertThrows(BusinessException.class, () -> interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void publicRequestStillRequiresGatewaySignature() {
        GatewayContextProperties properties = new GatewayContextProperties();
        properties.setSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        properties.setPublicEndpoints(java.util.List.of("POST /api/auth/sms/login/send"));
        GatewayIdentityService service = new GatewayIdentityService(properties);
        RedisService redis = mock(RedisService.class);
        GatewayIdentityInterceptor interceptor = new GatewayIdentityInterceptor(
                new AuthEndpointPolicy(properties), service, redis);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/sms/login/send");
        assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }
}
