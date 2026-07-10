package org.zipzip.zipzipserver.domain.sharedgroup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.zipzip.zipzipserver.domain.storage.ObjectStorageService;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class SharedGroupPurgeServiceIntegrationTest {

    // 실제 스케줄러가 병렬로 실행되지 않도록 최근 삭제 시각과 미래의 정리 기준을 사용한다.
    private static final Instant DELETED_AT = Instant.now().minus(Duration.ofDays(1));
    private static final Instant PURGE_BEFORE = Instant.now().plus(Duration.ofDays(1));

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired private SharedGroupPurgeService sharedGroupPurgeService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private ObjectStorageService objectStorageService;

    @BeforeEach
    void resetObjectStorageService() {
        reset(objectStorageService);
    }

    @Test
    void 스토리지_정리_성공_후_공유_그룹과_초대_코드를_물리_삭제한다() {
        Fixture fixture = insertFixture();

        sharedGroupPurgeService.purge(fixture.sharedGroupId(), PURGE_BEFORE);

        verify(objectStorageService)
                .deleteAll(List.of(fixture.originalObjectKey(), fixture.thumbnailObjectKey()));
        assertThat(count("shared_group", "id", fixture.sharedGroupId())).isZero();
        assertThat(count("shared_album", "id", fixture.sharedAlbumId())).isZero();
        assertThat(count("photo", "id", fixture.photoId())).isZero();
        assertThat(count("shared_album_photo", "photo_id", fixture.photoId())).isZero();
        assertThat(count("photo_like", "photo_id", fixture.photoId())).isZero();
        assertThat(count("photo_comment", "photo_id", fixture.photoId())).isZero();
        assertThat(count("shared_group_chat_message", "shared_group_id", fixture.sharedGroupId()))
                .isZero();
        assertThat(count("invite_code_reservation", "invite_code", fixture.inviteCode())).isZero();

        assertThat(
                        jdbcTemplate.update(
                                "insert into invite_code_reservation (invite_code) values (?)",
                                fixture.inviteCode()))
                .isEqualTo(1);
    }

    @Test
    void 스토리지_삭제_실패_시_그룹과_초대_코드를_유지하고_다음_실행에서_재시도한다() {
        Fixture fixture = insertFixture();
        doThrow(new IllegalStateException("Object Storage unavailable"))
                .when(objectStorageService)
                .deleteAll(any());

        assertThatThrownBy(
                        () -> sharedGroupPurgeService.purge(fixture.sharedGroupId(), PURGE_BEFORE))
                .isInstanceOf(IllegalStateException.class);

        assertThat(count("shared_group", "id", fixture.sharedGroupId())).isEqualTo(1);
        assertThat(count("photo", "id", fixture.photoId())).isEqualTo(1);
        assertThat(count("shared_album_photo", "photo_id", fixture.photoId())).isEqualTo(1);
        assertThat(count("invite_code_reservation", "invite_code", fixture.inviteCode()))
                .isEqualTo(1);

        reset(objectStorageService);
        sharedGroupPurgeService.purge(fixture.sharedGroupId(), PURGE_BEFORE);

        assertThat(count("shared_group", "id", fixture.sharedGroupId())).isZero();
        assertThat(count("photo", "id", fixture.photoId())).isZero();
        assertThat(count("invite_code_reservation", "invite_code", fixture.inviteCode())).isZero();
    }

    private Fixture insertFixture() {
        UUID appUserId = UUID.randomUUID();
        UUID sharedGroupId = UUID.randomUUID();
        UUID sharedAlbumId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        String inviteCode = "CODE" + UUID.randomUUID().toString().replace("-", "");
        String originalObjectKey = "photos/" + UUID.randomUUID() + "/original.jpg";
        String thumbnailObjectKey = "photos/" + UUID.randomUUID() + "/thumbnail.jpg";

        jdbcTemplate.update(
                "insert into app_user (id, apple_subject, display_name) values (?, ?, ?)",
                appUserId,
                "apple-" + appUserId,
                "집집이");
        jdbcTemplate.update(
                "insert into invite_code_reservation (invite_code) values (?)", inviteCode);
        jdbcTemplate.update(
                """
                insert into shared_group (
                    id, created_by_app_user_id, name, invite_code, deleted_at
                ) values (?, ?, ?, ?, ?)
                """,
                sharedGroupId,
                appUserId,
                "삭제된 그룹",
                inviteCode,
                Timestamp.from(DELETED_AT));
        jdbcTemplate.update(
                """
                insert into shared_group_membership (id, shared_group_id, app_user_id, role)
                values (?, ?, ?, 'HOST')
                """,
                UUID.randomUUID(),
                sharedGroupId,
                appUserId);
        jdbcTemplate.update(
                """
                insert into shared_group_chat_message (id, shared_group_id, app_user_id, content)
                values (?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                sharedGroupId,
                appUserId,
                "정리 대상 메시지");
        jdbcTemplate.update(
                """
                insert into shared_album (
                    id, shared_group_id, created_by_app_user_id, name, deleted_at
                ) values (?, ?, ?, ?, ?)
                """,
                sharedAlbumId,
                sharedGroupId,
                appUserId,
                "삭제된 앨범",
                Timestamp.from(DELETED_AT));
        jdbcTemplate.update(
                """
                insert into photo (
                    id, uploaded_by_app_user_id, original_object_key, thumbnail_object_key,
                    thumbnail_status, is_inferred, deleted_at
                ) values (?, ?, ?, ?, 'READY', false, ?)
                """,
                photoId,
                appUserId,
                originalObjectKey,
                thumbnailObjectKey,
                Timestamp.from(DELETED_AT));
        jdbcTemplate.update(
                """
                insert into shared_album_photo (id, shared_album_id, photo_id)
                values (?, ?, ?)
                """,
                UUID.randomUUID(),
                sharedAlbumId,
                photoId);
        jdbcTemplate.update(
                "insert into photo_like (id, photo_id, app_user_id) values (?, ?, ?)",
                UUID.randomUUID(),
                photoId,
                appUserId);
        jdbcTemplate.update(
                "insert into photo_comment (id, photo_id, app_user_id, content) values (?, ?, ?,"
                        + " ?)",
                UUID.randomUUID(),
                photoId,
                appUserId,
                "정리 대상 댓글");

        return new Fixture(
                sharedGroupId,
                sharedAlbumId,
                photoId,
                inviteCode,
                originalObjectKey,
                thumbnailObjectKey);
    }

    private int count(String tableName, String columnName, Object value) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where " + columnName + " = ?",
                Integer.class,
                value);
    }

    private record Fixture(
            UUID sharedGroupId,
            UUID sharedAlbumId,
            UUID photoId,
            String inviteCode,
            String originalObjectKey,
            String thumbnailObjectKey) {}
}
