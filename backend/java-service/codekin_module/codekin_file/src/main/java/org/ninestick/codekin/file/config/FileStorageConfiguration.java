package org.ninestick.codekin.file.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 使用成熟的 S3 SDK 访问 SeaweedFS，签名算法由 SDK 提供。
 *
 * @author ninestick
 */
@Configuration
@EnableConfigurationProperties(FileStorageProperties.class)
public class FileStorageConfiguration {
    /** 业务读写使用内部地址；配置 region 后不依赖自动地域探测。 */
    @Bean
    public MinioClient storageClient(FileStorageProperties properties) {
        MinioClient client = MinioClient.builder().endpoint(properties.getEndpoint().toString()).region(properties.getRegion())
                .credentials(properties.getAccessKey(), properties.getSecretKey()).build();
        client.setTimeout(5000, 15000, 30000);
        return client;
    }

    /** 预签名必须基于浏览器地址，不能先签内部域名再替换 host。 */
    @Bean
    public MinioClient publicStorageClient(FileStorageProperties properties) {
        MinioClient client = MinioClient.builder().endpoint(properties.getPublicEndpoint().toString()).region(properties.getRegion())
                .credentials(properties.getAccessKey(), properties.getSecretKey()).build();
        client.setTimeout(5000, 15000, 30000);
        return client;
    }
}
