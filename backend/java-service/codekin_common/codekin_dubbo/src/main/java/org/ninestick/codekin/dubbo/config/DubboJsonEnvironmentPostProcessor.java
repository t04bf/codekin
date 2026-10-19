package org.ninestick.codekin.dubbo.config;

import static org.apache.dubbo.common.constants.CommonConstants.DubboProperty.DUBBO_PREFER_JSON_FRAMEWORK_NAME;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 在配置加载和服务注册之前指定 Dubbo 内部 JSON 实现，避免扫描缺少依赖的可选扩展。
 * Dubbo 的 JsonUtils 直接读取 JVM 属性，普通 YAML 中的序列化配置无法替代此设置。
 *
 * @author ninestick
 */
public class DubboJsonEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    /** 与当前 Dubbo 所携带的 Fastjson2 实现一致。 */
    private static final String DEFAULT_JSON_FRAMEWORK = "fastjson2";

    /** 引用公共模块的服务自动生效；保留启动时显式指定的 JVM 属性。 */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        System.getProperties().putIfAbsent(DUBBO_PREFER_JSON_FRAMEWORK_NAME,
                environment.getProperty(DUBBO_PREFER_JSON_FRAMEWORK_NAME, DEFAULT_JSON_FRAMEWORK));
    }

    /** 必须先于配置数据加载，JSON 扩展一经初始化就会被 Dubbo 缓存。 */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
