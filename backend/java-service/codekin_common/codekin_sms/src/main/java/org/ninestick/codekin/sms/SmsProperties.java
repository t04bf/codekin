package org.ninestick.codekin.sms;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/**
 * 阿里云短信认证 SDK 的发送配置；凭据从运行环境读取。
 *
 * @author ninestick
 */
@ConfigurationProperties(prefix = "codekin.sms")
@Getter
@Setter
public class SmsProperties {
    /** 与已验证的阿里云示例保持一致的区域。 */
    private String region;
    /** 阿里云号码认证短信签名。 */
    private String signName;
    /** 阿里云号码认证短信模板编号。 */
    private String templateCode;

    /** 启动时检查 SDK 的必填参数；业务限流配置由使用方验证。 */
    public void validate() {
        Assert.hasText(region, "必须配置阿里云短信区域");
        Assert.hasText(signName, "必须配置阿里云短信签名");
        Assert.hasText(templateCode, "必须配置阿里云短信模板编号");
    }
}
