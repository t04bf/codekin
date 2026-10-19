package org.ninestick.codekin.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * 网关和业务服务使用同一组公开接口规则；其他路径默认要求认证。
 *
 * @author ninestick
 */
public class AuthEndpointPolicy {
    /** 配置解析后的方法与路径规则。 */
    private final List<PublicEndpoint> publicEndpoints;

    public AuthEndpointPolicy(GatewayContextProperties properties) {
        PathPatternParser parser = new PathPatternParser();
        List<PublicEndpoint> parsed = new ArrayList<>();
        for (String configured : properties.getPublicEndpoints()) {
            String[] parts = configured.split(" ", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalArgumentException("公开接口规则须为“HTTP方法 路径模板”：" + configured);
            }
            parsed.add(new PublicEndpoint(parts[0].toUpperCase(), parser.parse(parts[1])));
        }
        this.publicEndpoints = List.copyOf(parsed);
    }

    /** 严格按请求方法和路径判定，未列出的接口一律要求认证。 */
    public boolean isPublic(String method, String path) {
        PathContainer requestPath = PathContainer.parsePath(path);
        for (PublicEndpoint endpoint : publicEndpoints) {
            if (endpoint.method.equalsIgnoreCase(method) && endpoint.path.matches(requestPath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 单条公开接口规则。
     *
     * @author ninestick
     */
    private static final class PublicEndpoint {
        /** HTTP 方法。 */
        private final String method;
        /** Spring 路径模板。 */
        private final PathPattern path;

        private PublicEndpoint(String method, PathPattern path) {
            this.method = method;
            this.path = path;
        }
    }
}
