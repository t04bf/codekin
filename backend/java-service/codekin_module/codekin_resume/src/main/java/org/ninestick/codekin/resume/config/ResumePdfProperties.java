package org.ninestick.codekin.resume.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 打印组件与 Chromium 部署位置；空目录时读取打包资源。 @author ninestick */
@Getter @Setter @Component
@ConfigurationProperties(prefix = "codekin.resume-pdf")
public class ResumePdfProperties {
    private String assetsDirectory = "";
    private String executablePath = "";
    private int timeoutMillis = 30000;
    private int maxConcurrent = 2;
}
