package xyz.kuailemao.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 阿里云 OSS 配置，对应 oss.*
 */
@Data
@Validated
@ConfigurationProperties(prefix = "oss")
public class OssProperties {

    /**
     * 地域节点，不含协议，例如 oss-cn-hangzhou.aliyuncs.com
     */
    @NotBlank
    private String endpoint;

    @NotBlank
    private String accessKey;

    @NotBlank
    private String secretKey;

    @NotBlank
    private String bucketName;

    /**
     * 可选自定义访问域名，例如 https://image.example.com
     */
    private String domain = "";
}
