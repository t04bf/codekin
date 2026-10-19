package org.ninestick.codekin.dubbo.config;

import static org.apache.dubbo.common.constants.CommonConstants.DubboProperty.DUBBO_PREFER_JSON_FRAMEWORK_NAME;
import static org.junit.jupiter.api.Assertions.*;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.dubbo.common.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;

/**
 * 使用缺少 Fastjson 旧 API、Gson 和 Jackson 2 的独立 JVM，复现并验证注册路径的 JSON 初始化。
 *
 * @author ninestick
 */
class DubboJsonEnvironmentPostProcessorTest {
    /** 子进程输出目录；测试不连接任何注册中心或中间件。 */
    @TempDir
    Path outputDirectory;

    @Test
    void explicitJvmPreferenceIsPreserved() {
        String previous = System.getProperty(DUBBO_PREFER_JSON_FRAMEWORK_NAME);
        try {
            System.setProperty(DUBBO_PREFER_JSON_FRAMEWORK_NAME, "explicit-preference");
            new DubboJsonEnvironmentPostProcessor().postProcessEnvironment(new MockEnvironment(), new SpringApplication());
            assertEquals("explicit-preference", System.getProperty(DUBBO_PREFER_JSON_FRAMEWORK_NAME));
        } finally {
            if (previous == null) System.clearProperty(DUBBO_PREFER_JSON_FRAMEWORK_NAME);
            else System.setProperty(DUBBO_PREFER_JSON_FRAMEWORK_NAME, previous);
        }
    }

    @Test
    void springBootAutomaticallySelectsFastjson2WithoutOptionalLibraries() throws Exception {
        // 使用完整测试 classpath，但排除会掩盖生产缺依赖问题的可选 JSON 库。
        String classpath = Arrays.stream(System.getProperty("surefire.test.class.path",
                        System.getProperty("java.class.path")).split(File.pathSeparator))
                .filter(path -> !path.contains("/com/alibaba/fastjson/")
                        && !path.contains("/com/google/code/gson/") && !path.contains("/com/fasterxml/jackson/"))
                .collect(Collectors.joining(File.pathSeparator));
        for (String mode : new String[] {"unconfigured", "boot"}) {
            Path output = outputDirectory.resolve(mode + ".log");
            Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", classpath, JsonInitializationProbe.class.getName(), mode)
                    .redirectErrorStream(true).redirectOutput(output.toFile()).start();
            // 有限等待；失败时释放子进程，避免占用端口或遗留 JVM。
            try {
                assertTrue(process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS), "JSON 初始化测试超时");
                String log = Files.readString(output);
                if ("unconfigured".equals(mode)) {
                    assertNotEquals(0, process.exitValue(), log);
                    assertTrue(log.contains("com/alibaba/fastjson/JSONException"), log);
                } else {
                    assertEquals(0, process.exitValue(), log);
                    assertTrue(log.contains("JSON_PROBE_OK"), log);
                }
            } finally {
                if (process.isAlive()) process.destroyForcibly();
            }
        }
    }

    /** 独立 JVM 入口，避免测试进程已缓存的 JsonUtils 或系统属性影响结论。 */
    public static class JsonInitializationProbe {
        public static void main(String[] args) {
            System.clearProperty(DUBBO_PREFER_JSON_FRAMEWORK_NAME);
            if ("unconfigured".equals(args[0])) {
                JsonUtils.toJson(Map.of("status", "ok"));
                return;
            }
            SpringApplication application = new SpringApplication(JsonOnlyConfiguration.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setDefaultProperties(Map.of("spring.main.banner-mode", "off"));
            try (var context = application.run()) {
                if (!"fastjson2".equals(JsonUtils.getJson().getName())) {
                    throw new IllegalStateException("Dubbo 未选择 Fastjson2");
                }
                String json = JsonUtils.toJson(Map.of("status", "ok"));
                Map<?, ?> decoded = JsonUtils.toJavaObject(json, Map.class);
                if (!"ok".equals(decoded.get("status"))) {
                    throw new IllegalStateException("Dubbo JSON 编解码失败");
                }
                System.out.println("JSON_PROBE_OK");
            }
        }
    }

    /** 仅创建 Spring 上下文，验证真实 spring.factories 装配，不启动业务自动配置。 */
    @Configuration(proxyBeanMethods = false)
    public static class JsonOnlyConfiguration { }
}
