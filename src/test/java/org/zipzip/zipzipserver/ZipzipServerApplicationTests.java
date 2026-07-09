package org.zipzip.zipzipserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.zipzip.zipzipserver.domain.auth.repository.RefreshTokenRepository;
import org.zipzip.zipzipserver.domain.user.repository.AppUserRepository;

@SpringBootTest(
        properties = {
            "spring.config.import=optional:classpath:config/application-secret.yml",
            "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
            "jwt.issuer=zipzip-server-test",
            "jwt.access-secret=test-access-secret-must-be-at-least-32-characters",
            "jwt.refresh-secret=test-refresh-secret-must-be-at-least-32-characters",
            "jwt.access-token-expiration=30m",
            "jwt.refresh-token-expiration=14d",
            "apple.team-id=test-apple-team-id",
            "apple.client-id=test-apple-client-id",
            "apple.key-id=test-apple-key-id",
            "apple.private-key=test-apple-private-key"
        })
class ZipzipServerApplicationTests {

    @MockitoBean private AppUserRepository appUserRepository;

    @MockitoBean private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void contextLoads() {}
}
