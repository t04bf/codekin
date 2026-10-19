package org.ninestick.codekin.gateway;

import lombok.extern.slf4j.Slf4j;
import org.ninestick.codekin.security.SecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Spring Cloud Gateway WebFlux 启动入口。
 *
 * @author ninestick
 */
@Slf4j
@SpringBootApplication
@Import(SecurityConfiguration.class)
public class CodekinGatewayApplication {
    static void main(String[] args) {
        SpringApplication.run(CodekinGatewayApplication.class, args);
        log.info("CodekinGatewayApplication started");
    }
}
