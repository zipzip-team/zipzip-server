package org.zipzip.zipzipserver.domain.auth.config;

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
@ConfigurationProperties(prefix = "apple")
public class AppleOAuthProperties {

    @NotBlank
    private String teamId;

    @NotBlank
    private String clientId;

    @NotBlank
    private String keyId;

    @NotBlank
    private String privateKey;

    public String normalizedPrivateKey() {
        return privateKey.strip();
    }
}
