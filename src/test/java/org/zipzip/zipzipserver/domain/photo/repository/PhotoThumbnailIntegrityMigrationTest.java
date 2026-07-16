package org.zipzip.zipzipserver.domain.photo.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PhotoThumbnailIntegrityMigrationTest {

    private static final String PREVIOUS_MIGRATION_VERSION = "20260710140929";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Test
    void 썸네일_READY_상태는_비어있지_않은_객체_키를_가져야_한다() throws SQLException {
        migrateUpToPreviousVersion();
        UUID userId = insertUser();
        UUID malformedPhotoId = insertPhoto(userId, "READY", " \t\n");

        migrateLatest();

        assertThat(findThumbnailStatus(malformedPhotoId)).isEqualTo("FAILED");
        assertThatThrownBy(() -> insertPhoto(userId, "READY", null))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertPhoto(userId, "READY", "  "))
                .isInstanceOf(SQLException.class);
        assertThat(insertPhoto(userId, "PENDING", null)).isNotNull();
        assertThat(insertPhoto(userId, "READY", "photos/thumbnail.jpg")).isNotNull();
    }

    private void migrateUpToPreviousVersion() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(PREVIOUS_MIGRATION_VERSION)
                .load()
                .migrate();
    }

    private void migrateLatest() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private UUID insertUser() throws SQLException {
        UUID userId = UUID.randomUUID();
        try (Connection connection = connection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                insert into app_user (id, apple_subject, display_name)
                                values (?, ?, '사용자')
                                """)) {
            statement.setObject(1, userId);
            statement.setString(2, "apple-subject-" + userId);
            statement.executeUpdate();
        }
        return userId;
    }

    private UUID insertPhoto(UUID userId, String thumbnailStatus, String thumbnailObjectKey)
            throws SQLException {
        UUID photoId = UUID.randomUUID();
        try (Connection connection = connection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                """
                                insert into photo (
                                    id, uploaded_by_app_user_id, original_object_key, thumbnail_object_key,
                                    thumbnail_status, is_inferred
                                )
                                values (?, ?, ?, ?, ?, false)
                                """)) {
            statement.setObject(1, photoId);
            statement.setObject(2, userId);
            statement.setString(3, "photos/" + photoId + "/original.jpg");
            statement.setString(4, thumbnailObjectKey);
            statement.setString(5, thumbnailStatus);
            statement.executeUpdate();
        }
        return photoId;
    }

    private String findThumbnailStatus(UUID photoId) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "select thumbnail_status from photo where id = ?")) {
            statement.setObject(1, photoId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString("thumbnail_status");
            }
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
