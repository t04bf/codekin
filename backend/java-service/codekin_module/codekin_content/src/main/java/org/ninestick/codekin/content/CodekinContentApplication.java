package org.ninestick.codekin.content;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 内容服务启动入口，复用公共 WebMVC 配置与 Nacos 配置中心。
 *
 * @author ninestick
 */
@SpringBootApplication
public class CodekinContentApplication {

    /** 启动内容服务，业务配置从 Nacos 读取。 */
    public static void main(String[] args) {
        SpringApplication.run(CodekinContentApplication.class, args);
    }
}
