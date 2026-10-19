package org.ninestick.codekin.file;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 文件服务，独立提供直传签名、上传确认及 Dubbo 文件核验。
 *
 * @author ninestick
 */
@EnableDubbo
@EnableScheduling
@MapperScan("org.ninestick.codekin.file.mapper")
@SpringBootApplication
public class CodekinFileApplication {
    /** 启动文件服务，业务配置从 Nacos 读取。 */
    public static void main(String[] args) {
        SpringApplication.run(CodekinFileApplication.class, args);
    }
}

