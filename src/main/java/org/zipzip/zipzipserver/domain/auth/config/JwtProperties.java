package org.zipzip.zipzipserver.domain.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    @NotBlank private String issuer;

    @NotBlank
    @Size(min = 32)
    private String secret;

    @NotNull private Duration accessTokenExpiration;

    @NotNull private Duration refreshTokenExpiration;
}
