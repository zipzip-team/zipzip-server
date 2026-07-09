package org.zipzip.zipzipserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        properties = {
            "spring.config.import=optional:classpath:config/application-secret.yml",
            "jwt.issuer=zipzip-server-test",
            "jwt.access-secret=test-access-secret-must-be-at-least-32-characters",
            "jwt.refresh-secret=test-refresh-secret-must-be-at-least-32-characters",
            "jwt.access-token-expiration=30m",
            "jwt.refresh-token-expiration=14d",
            "idempotency.auth-response-encryption-key=test-idempotency-secret-must-be-at-least-32-characters",
            "idempotency.auth-response-ttl=10m",
            "apple.team-id=test-apple-team-id",
            "apple.client-id=test-apple-client-id",
            "apple.key-id=test-apple-key-id",
            "apple.private-key=test-apple-private-key"
        })
@Testcontainers(disabledWithoutDocker = true)
class ZipzipServerApplicationTests {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("zipzip_context_test")
                    .withUsername("zipzip")
                    .withPassword("zipzip");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Test
    void contextLoads() {}
}
