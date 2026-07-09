package org.zipzip.zipzipserver.global.idempotency;

import jakarta.validation.constraints.NotBlank;
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
@ConfigurationProperties(prefix = "idempotency")
public class IdempotencyProperties {

    @NotBlank private String authResponseEncryptionKey;

    private Duration authResponseTtl = Duration.ofMinutes(10);
}
