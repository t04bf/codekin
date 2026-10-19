package org.ninestick.codekin.resume;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 简历服务启动入口。
 *
 * @author ninestick
 */
@EnableDubbo
@SpringBootApplication
public class CodekinResumeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodekinResumeApplication.class, args);
    }
}
