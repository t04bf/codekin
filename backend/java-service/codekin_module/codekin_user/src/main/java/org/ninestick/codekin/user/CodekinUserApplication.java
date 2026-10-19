package org.ninestick.codekin.user;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.ninestick.codekin.security.SecurityConfiguration;
import org.ninestick.codekin.email.EmailConfiguration;
import org.ninestick.codekin.user.config.UserEmailProperties;
import org.ninestick.codekin.user.config.UserAvatarProperties;
import org.ninestick.codekin.user.config.UserSmsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * 用户服务启动入口。
 *
 * @author ninestick
 */
@Slf4j
@EnableDubbo
@SpringBootApplication
@Import({SecurityConfiguration.class, EmailConfiguration.class})
@EnableConfigurationProperties({UserSmsProperties.class, UserEmailProperties.class, UserAvatarProperties.class})
public class CodekinUserApplication {

    static void main(String[] args) {
        SpringApplication.run(CodekinUserApplication.class, args);
        log.info("CodekinUserApplication started!");
    }
}
