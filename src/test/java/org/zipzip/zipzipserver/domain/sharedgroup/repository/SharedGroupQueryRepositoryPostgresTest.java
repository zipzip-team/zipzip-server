package org.zipzip.zipzipserver.domain.sharedgroup.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        properties = {
            "jwt.issuer=zipzip-server-test",
            "jwt.access-secret=test-access-secret-must-be-at-least-32-characters",
            "jwt.refresh-secret=test-refresh-secret-must-be-at-least-32-characters",
            "jwt.access-token-expiration=30m",
            "jwt.refresh-token-expiration=14d",
            "apple.team-id=test-apple-team-id",
            "apple.client-id=test-apple-client-id",
            "apple.key-id=test-apple-key-id",
            "apple.private-key=test-apple-private-key",
            "idempotency.auth-response-encryption-key=test-idempotency-key",
            "spring.flyway.enabled=true"
        })
class SharedGroupQueryRepositoryPostgresTest {

    private static final UUID APP_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID FIRST_GROUP_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SECOND_GROUP_ID =
            UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID DELETED_GROUP_ID =
            UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID FIRST_ALBUM_ID =
            UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID SECOND_ALBUM_ID =
            UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID PHOTO_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID FIRST_MEMBERSHIP_ID =
            UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID SECOND_MEMBERSHIP_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID DELETED_GROUP_MEMBERSHIP_ID =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID FIRST_ALBUM_PHOTO_ID =
            UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID SECOND_ALBUM_PHOTO_ID =
            UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER =
            new PostgreSQLContainer<>("postgres:18");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL_CONTAINER::getPassword);
        registry.add(
                "spring.datasource.driver-class-name", POSTGRESQL_CONTAINER::getDriverClassName);
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SharedGroupQueryRepository sharedGroupQueryRepository;

    @Test
    void 목록은_활성_그룹만_참여일시_내림차순과_id_오름차순으로_조회한다() {
        insertFixture();

        List<SharedGroupQueryRepository.SharedGroupListRow> rows =
                sharedGroupQueryRepository.findMySharedGroups(APP_USER_ID, null, null, 10);

        assertThat(rows)
                .extracting(SharedGroupQueryRepository.SharedGroupListRow::id)
                .containsExactly(FIRST_GROUP_ID, SECOND_GROUP_ID);
    }

    @Test
    void 상세_조회는_사진을_distinct로_집계한다() {
        insertFixture();

        SharedGroupQueryRepository.SharedGroupDetailRow row =
                sharedGroupQueryRepository.findDetail(APP_USER_ID, FIRST_GROUP_ID).orElseThrow();

        assertThat(row.memberCount()).isEqualTo(1);
        assertThat(row.sharedAlbumCount()).isEqualTo(2);
        assertThat(row.photoCount()).isEqualTo(1);
    }

    private void insertFixture() {
        jdbcTemplate.update(
                """
                insert into app_user (id, apple_subject, display_name, created_at, updated_at)
                values
                    (?, 'apple-subject', '집집이', now(), now()),
                    (?, 'other-subject', '다른 사용자', now(), now())
                on conflict (id) do nothing
                """,
                APP_USER_ID,
                OTHER_USER_ID);
        jdbcTemplate.update(
                """
                insert into invite_code_reservation (invite_code)
                values ('INVITE01'), ('INVITE02'), ('INVITE03')
                on conflict (invite_code) do nothing
                """);
        jdbcTemplate.update(
                """
                insert into shared_group (
                    id, created_by_app_user_id, name, invite_code, created_at, updated_at, deleted_at
                )
                values
                    (?, ?, '첫 번째 그룹', 'INVITE01', now(), now(), null),
                    (?, ?, '두 번째 그룹', 'INVITE02', now(), now(), null),
                    (?, ?, '삭제 그룹', 'INVITE03', now(), now(), now())
                on conflict (id) do nothing
                """,
                FIRST_GROUP_ID,
                APP_USER_ID,
                SECOND_GROUP_ID,
                APP_USER_ID,
                DELETED_GROUP_ID,
                APP_USER_ID);
        jdbcTemplate.update(
                """
                insert into shared_group_membership (
                    id, shared_group_id, app_user_id, role, created_at, updated_at
                )
                values
                    (?, ?, ?, 'HOST', ?, now()),
                    (?, ?, ?, 'MEMBER', ?, now()),
                    (?, ?, ?, 'MEMBER', ?, now())
                on conflict (shared_group_id, app_user_id) do nothing
                """,
                SECOND_MEMBERSHIP_ID,
                SECOND_GROUP_ID,
                APP_USER_ID,
                Timestamp.from(Instant.parse("2026-07-10T00:00:00Z")),
                FIRST_MEMBERSHIP_ID,
                FIRST_GROUP_ID,
                APP_USER_ID,
                Timestamp.from(Instant.parse("2026-07-10T00:00:00Z")),
                DELETED_GROUP_MEMBERSHIP_ID,
                DELETED_GROUP_ID,
                APP_USER_ID,
                Timestamp.from(Instant.parse("2026-07-11T00:00:00Z")));
        jdbcTemplate.update(
                """
                insert into shared_album (
                    id, shared_group_id, created_by_app_user_id, name, created_at, updated_at, deleted_at
                )
                values
                    (?, ?, ?, '앨범 1', now(), now(), null),
                    (?, ?, ?, '앨범 2', now(), now(), null)
                on conflict (id) do nothing
                """,
                FIRST_ALBUM_ID,
                FIRST_GROUP_ID,
                APP_USER_ID,
                SECOND_ALBUM_ID,
                FIRST_GROUP_ID,
                APP_USER_ID);
        jdbcTemplate.update(
                """
                insert into photo (
                    id, uploaded_by_app_user_id, original_object_key, thumbnail_status,
                    is_inferred, created_at, updated_at
                )
                values (?, ?, 'photos/original.jpg', 'PENDING', false, now(), now())
                on conflict (id) do nothing
                """,
                PHOTO_ID,
                APP_USER_ID);
        jdbcTemplate.update(
                """
                insert into shared_album_photo (id, shared_album_id, photo_id, created_at, updated_at)
                values
                    (?, ?, ?, now(), now()),
                    (?, ?, ?, now(), now())
                on conflict (shared_album_id, photo_id) do nothing
                """,
                FIRST_ALBUM_PHOTO_ID,
                FIRST_ALBUM_ID,
                PHOTO_ID,
                SECOND_ALBUM_PHOTO_ID,
                SECOND_ALBUM_ID,
                PHOTO_ID);
    }
}
