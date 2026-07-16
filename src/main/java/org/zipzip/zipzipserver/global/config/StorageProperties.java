package org.zipzip.zipzipserver.global.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    @NotBlank private String endpoint;

    @NotBlank private String region;

    @NotBlank private String bucket;

    @NotBlank private String accessKey;

    @NotBlank private String secretKey;

    private int httpMaxConnections = 50;
}
