package org.zipzip.zipzipserver.global.idempotency;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class ApiIdempotencyRecordMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Test
    void api_idempotency_record_마이그레이션은_unique_key를_보장한다() throws SQLException {
        migrate();
        UUID idempotencyKey = UUID.randomUUID();

        try (Connection connection =
                DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            insertProcessingRecord(
                    connection,
                    UUID.randomUUID(),
                    "AUTH_REFRESH:user-id",
                    idempotencyKey,
                    "a".repeat(64));

            assertThatThrownBy(
                            () ->
                                    insertProcessingRecord(
                                            connection,
                                            UUID.randomUUID(),
                                            "AUTH_REFRESH:user-id",
                                            idempotencyKey,
                                            "b".repeat(64)))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private void insertProcessingRecord(
            Connection connection, UUID id, String scope, UUID idempotencyKey, String requestHash)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement(
                        """
                        insert into api_idempotency_record (
                            id,
                            scope,
                            idempotency_key,
                            http_method,
                            api_path,
                            request_hash,
                            status,
                            expires_at
                        )
                        values (?, ?, ?, 'POST', '/api/v1/auth/refresh', ?, 'PROCESSING', now() + interval '10 minutes')
                        """)) {
            statement.setObject(1, id);
            statement.setString(2, scope);
            statement.setObject(3, idempotencyKey);
            statement.setString(4, requestHash);
            statement.executeUpdate();
        }
    }
}
