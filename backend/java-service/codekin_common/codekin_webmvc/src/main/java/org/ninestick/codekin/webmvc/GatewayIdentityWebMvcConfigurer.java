package org.ninestick.codekin.webmvc;

import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 将网关身份拦截器应用于业务服务的所有 API 路径。
 *
 * @author ninestick
 */
public class GatewayIdentityWebMvcConfigurer implements WebMvcConfigurer {
    /** 已装配的共享拦截器。 */
    private final GatewayIdentityInterceptor interceptor;

    public GatewayIdentityWebMvcConfigurer(GatewayIdentityInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**");
    }
}
