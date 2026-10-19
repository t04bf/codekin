package org.ninestick.codekin.file.config;

import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 对象存储基础设施配置，业务用途限制由 UploadType 枚举定义。
 *
 * @author ninestick
 */
@Getter
@Setter
@Validated
@ConfigurationProperties("codekin.file.storage")
public class FileStorageProperties {
    /** 文件服务实际访问的 S3 地址。 */
    @NotNull private URI endpoint;
    /** 浏览器可达的 S3 地址，签名及上传表单统一使用此地址。 */
    @NotNull private URI publicEndpoint;
    /** S3 签名地域参数，不表示实际部署地点。 */
    @NotBlank private String region;
    /** 存储桶。 */
    @NotBlank private String bucket;
    /** 服务端 Access Key，不输出到日志。 */
    @NotBlank private String accessKey;
    /** 服务端 Secret Key，不返回前端。 */
    @NotBlank private String secretKey;
    /** POST 签名有效期；确认期限独立且更长。 */
    @NotNull private Duration signatureTtl = Duration.ofMinutes(5);
    /** 用户允许完成上传并确认的时间。 */
    @NotNull private Duration confirmationTtl = Duration.ofMinutes(15);
    /** 已审核图片 GET URL 有效期。 */
    @NotNull private Duration accessTtl = Duration.ofHours(1);
    /** 用户签名次数的计数窗口。 */
    @NotNull private Duration signatureWindow = Duration.ofMinutes(10);
    /** 允许跨域直传的明确前端来源，不使用通配来源。 */
    @NotEmpty private List<@NotBlank String> allowedOrigins;
    /** 单实例同时解码图片的上限，避免多个高像素文件耗尽堆内存。 */
    @Min(1) @Max(8) private int maxConcurrentImageProcessing = 2;
    /** 分批清理过期临时上传时每批最大记录数。 */
    @Min(1) @Max(500) private int cleanupBatchSize = 100;

    /** 防止无效期限导致凭证无限有效或清理仍有效的上传。 */
    @AssertTrue(message = "文件有效期必须为正数且确认期限不得早于签名期限")
    public boolean isLifetimeValid() {
        return signatureTtl != null && signatureTtl.toSeconds() > 0 && signatureTtl.toSeconds() <= 3600
                && confirmationTtl != null && confirmationTtl.compareTo(signatureTtl) >= 0
                && confirmationTtl.toHours() < 24 && accessTtl != null && accessTtl.toSeconds() > 0
                && accessTtl.toSeconds() <= 604800 && signatureWindow != null && signatureWindow.toSeconds() > 0;
    }

    /** 防止将无效 scheme 地址用于签名及服务端请求。 */
    @AssertTrue(message = "对象存储地址必须使用 http 或 https")
    public boolean isEndpointValid() {
        return endpoint != null && publicEndpoint != null
                && List.of("http", "https").contains(endpoint.getScheme())
                && List.of("http", "https").contains(publicEndpoint.getScheme());
    }
}

