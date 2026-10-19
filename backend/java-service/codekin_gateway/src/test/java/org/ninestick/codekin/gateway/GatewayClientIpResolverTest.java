package org.ninestick.codekin.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

/**
 * 验证直连、可信代理链和伪造转发头的客户端 IP 判定。
 *
 * @author ninestick
 */
class GatewayClientIpResolverTest {
    @Test
    void directModeIgnoresForgedForwardedFor() {
        GatewayClientIpResolver resolver = new GatewayClientIpResolver(new GatewayClientIpProperties());

        String ip = resolver.resolve(MockServerHttpRequest.get("/")
                .remoteAddress(new InetSocketAddress("192.0.2.10", 80))
                .header(GatewayClientIpResolver.FORWARDED_FOR_HEADER, "198.51.100.99").build());

        assertEquals("192.0.2.10", ip);
    }

    @Test
    void trustedProxyUsesRightmostUntrustedAddress() {
        GatewayClientIpProperties properties = new GatewayClientIpProperties();
        properties.setMode(GatewayClientIpProperties.Mode.TRUSTED_PROXY);
        properties.setTrustedProxies(List.of("10.0.0.0/24", "127.0.0.1/32"));
        GatewayClientIpResolver resolver = new GatewayClientIpResolver(properties);

        String ip = resolver.resolve(MockServerHttpRequest.get("/")
                .remoteAddress(new InetSocketAddress("10.0.0.8", 80))
                .header(GatewayClientIpResolver.FORWARDED_FOR_HEADER,
                        "203.0.113.200, 198.51.100.5, 10.0.0.7").build());

        assertEquals("198.51.100.5", ip);
    }

    @Test
    void untrustedPeerCannotSetClientIpThroughForwardedHeader() {
        GatewayClientIpProperties properties = new GatewayClientIpProperties();
        properties.setMode(GatewayClientIpProperties.Mode.TRUSTED_PROXY);
        properties.setTrustedProxies(List.of("10.0.0.0/24"));
        GatewayClientIpResolver resolver = new GatewayClientIpResolver(properties);

        String ip = resolver.resolve(MockServerHttpRequest.get("/")
                .remoteAddress(new InetSocketAddress("192.0.2.10", 80))
                .header(GatewayClientIpResolver.FORWARDED_FOR_HEADER, "198.51.100.99").build());

        assertEquals("192.0.2.10", ip);
    }

    @Test
    void trustedProxyWithoutValidForwardedHeaderFailsClosed() {
        GatewayClientIpProperties properties = new GatewayClientIpProperties();
        properties.setMode(GatewayClientIpProperties.Mode.TRUSTED_PROXY);
        properties.setTrustedProxies(List.of("10.0.0.0/24"));
        GatewayClientIpResolver resolver = new GatewayClientIpResolver(properties);

        assertThrows(IllegalStateException.class, () -> resolver.resolve(MockServerHttpRequest.get("/")
                .remoteAddress(new InetSocketAddress("10.0.0.8", 80)).build()));
        assertThrows(IllegalStateException.class, () -> resolver.resolve(MockServerHttpRequest.get("/")
                .remoteAddress(new InetSocketAddress("10.0.0.8", 80))
                .header(GatewayClientIpResolver.FORWARDED_FOR_HEADER, "unknown").build()));
    }

    @Test
    void nacosConfigurationBindsProxyModeAndCommaSeparatedCidrs() {
        GatewayClientIpProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
                "codekin.gateway.client-ip.mode", "TRUSTED_PROXY",
                "codekin.gateway.client-ip.trusted-proxies", "127.0.0.1/32,10.0.0.0/24")))
                .bind("codekin.gateway.client-ip", Bindable.of(GatewayClientIpProperties.class)).get();

        assertEquals(GatewayClientIpProperties.Mode.TRUSTED_PROXY, properties.getMode());
        assertEquals(List.of("127.0.0.1/32", "10.0.0.0/24"), properties.getTrustedProxies());
        assertThrows(IllegalArgumentException.class, () -> {
            properties.setTrustedProxies(List.of("0.0.0.0/0"));
            new GatewayClientIpResolver(properties);
        });
        properties.setTrustedProxies(List.of());
        assertThrows(IllegalArgumentException.class, () -> new GatewayClientIpResolver(properties));
    }
}
