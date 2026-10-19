package org.ninestick.codekin.sms;

import static org.ninestick.codekin.sms.SmsConstants.DYPNSAPI_ENDPOINT;

import com.aliyun.auth.credentials.provider.EnvironmentVariableCredentialProvider;
import com.aliyun.sdk.service.dypnsapi20170525.AsyncClient;
import darabonba.core.client.ClientOverrideConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 为引用短信模块的服务装配阿里云 SDK 和发送组件。
 *
 * @author ninestick
 */
@AutoConfiguration
@EnableConfigurationProperties(SmsProperties.class)
public class SmsAutoConfiguration {
    /** 异步 SDK 沿用示例配置；凭据直接从环境变量读取以避开缺失的配置文件。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(AsyncClient.class)
    public AsyncClient dypnsapiClient(SmsProperties properties) {
        properties.validate();
        return AsyncClient.builder()
                .region(properties.getRegion())
                .credentialsProvider(EnvironmentVariableCredentialProvider.create())
                .overrideConfiguration(ClientOverrideConfiguration.create()
                        .setEndpointOverride(DYPNSAPI_ENDPOINT))
                .build();
    }

    /** 各业务服务共享短信发送适配器，限流和核验留在调用方。 */
    @Bean
    @ConditionalOnMissingBean(SmsSender.class)
    public SmsSender smsSender(AsyncClient client, SmsProperties properties) {
        properties.validate();
        return new AliyunSmsSender(client, properties);
    }
}
