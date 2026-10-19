package org.ninestick.codekin.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.ninestick.codekin.model.common.BusinessException;

/**
 * 网关签名绑定用户与请求目标，公开路径按方法精确匹配。
 *
 * @author ninestick
 */
class GatewayIdentityServiceTest {
    @Test
    void signedIdentityCannotBeModifiedOrUsedOnAnotherPath() {
        GatewayContextProperties properties = new GatewayContextProperties();
        properties.setSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        GatewayIdentityService service = new GatewayIdentityService(properties);
        AuthenticatedUser user = AuthenticatedUser.builder().userId(123L).sessionId("jti").build();
        GatewaySignedIdentity signed = service.sign(user, "127.0.0.1", "GET", "/api/users/me?view=basic");

        assertEquals(123L, service.verify(signed, "GET", "/api/users/me?view=basic").getUser().getUserId());
        assertEquals("127.0.0.1", service.verify(signed, "GET", "/api/users/me?view=basic").getClientAddress());
        assertThrows(BusinessException.class, () -> service.verify(signed, "POST", "/api/users/me?view=basic"));
        assertThrows(BusinessException.class, () -> service.verify(signed, "GET", "/api/users/me"));
        GatewaySignedIdentity forged = GatewaySignedIdentity.builder()
                .userId("456")
                .sessionId(signed.getSessionId())
                .timestamp(signed.getTimestamp())
                .nonce(signed.getNonce())
                .signature(signed.getSignature())
                .clientAddress(signed.getClientAddress())
                .build();
        assertThrows(BusinessException.class, () -> service.verify(forged, "GET", "/api/users/me?view=basic"));
        GatewaySignedIdentity forgedAddress = GatewaySignedIdentity.builder()
                .userId(signed.getUserId())
                .sessionId(signed.getSessionId())
                .timestamp(signed.getTimestamp())
                .nonce(signed.getNonce())
                .signature(signed.getSignature())
                .clientAddress("127.0.0.2")
                .build();
        assertThrows(BusinessException.class,
                () -> service.verify(forgedAddress, "GET", "/api/users/me?view=basic"));
    }

    @Test
    void anonymousIdentityCarriesSignedClientAddress() {
        GatewayContextProperties properties = new GatewayContextProperties();
        properties.setSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        GatewayIdentityService service = new GatewayIdentityService(properties);
        GatewaySignedIdentity signed = service.sign(null, "127.0.0.1", "POST", "/api/auth/sms/login/send");

        assertEquals(null, service.verify(signed, "POST", "/api/auth/sms/login/send").getUser());
        assertEquals("127.0.0.1", service.verify(signed, "POST", "/api/auth/sms/login/send").getClientAddress());
    }

    @Test
    void publicRuleMatchesMethodAndPathOnly() {
        GatewayContextProperties properties = new GatewayContextProperties();
        properties.setPublicEndpoints(List.of("GET /api/users/{userId:[0-9]+}"));
        AuthEndpointPolicy policy = new AuthEndpointPolicy(properties);

        assertTrue(policy.isPublic("GET", "/api/users/123"));
        assertFalse(policy.isPublic("POST", "/api/users/123"));
        assertFalse(policy.isPublic("GET", "/api/users/me"));
    }
}
