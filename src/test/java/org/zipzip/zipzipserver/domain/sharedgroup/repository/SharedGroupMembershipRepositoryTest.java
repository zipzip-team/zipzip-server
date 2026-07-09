package org.zipzip.zipzipserver.domain.sharedgroup.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.zipzip.zipzipserver.domain.device.repository.DeviceRepository;
import org.zipzip.zipzipserver.domain.device.repository.DeviceRow;

@DataJpaTest(properties = "spring.config.import=optional:classpath:config/application-secret.yml")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class SharedGroupMembershipRepositoryTest {

    private static final UUID GROUP_ID = UUID.fromString("10000000-0000-0000-0000-000000000000");
    private static final UUID DELETED_GROUP_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID HOST_ID = UUID.fromString("20000000-0000-0000-0000-000000000000");
    private static final UUID MEMBER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID DELETED_USER_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID STRANGER_ID = UUID.fromString("20000000-0000-0000-0000-000000000003");
    private static final Instant BASE_TIME = Instant.parse("2026-07-03T10:15:30Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("zipzip_test")
                    .withUsername("zipzip")
                    .withPassword("zipzip");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SharedGroupMembershipRepository sharedGroupMembershipRepository;
    @Autowired private DeviceRepository deviceRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
                """
                truncate table
                    device,
                    shared_group_membership,
                    shared_group,
                    invite_code_reservation,
                    app_user
                restart identity cascade
                """);

        insertUser(HOST_ID, "apple-host", "방장", null);
        insertUser(MEMBER_ID, "apple-member", "멤버", null);
        insertUser(DELETED_USER_ID, "apple-deleted", "탈퇴한 사용자", BASE_TIME.plusSeconds(600));
        insertUser(STRANGER_ID, "apple-stranger", "외부인", null);
        insertInviteCode("INVITE1");
        insertInviteCode("INVITE2");
        insertGroup(GROUP_ID, HOST_ID, "공유 그룹", "INVITE1", null);
        insertGroup(DELETED_GROUP_ID, HOST_ID, "삭제 그룹", "INVITE2", BASE_TIME.plusSeconds(600));
        insertMembership(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                GROUP_ID,
                HOST_ID,
                "HOST",
                BASE_TIME);
        insertMembership(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                GROUP_ID,
                MEMBER_ID,
                "MEMBER",
                BASE_TIME.plusSeconds(60));
        insertMembership(
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                GROUP_ID,
                DELETED_USER_ID,
                "MEMBER",
                BASE_TIME.plusSeconds(120));
        insertMembership(
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                DELETED_GROUP_ID,
                STRANGER_ID,
                "HOST",
                BASE_TIME);
        insertDevice(
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                HOST_ID,
                "iPhone 16 Pro",
                BASE_TIME,
                null);
        insertDevice(
                UUID.fromString("30000000-0000-0000-0000-000000000002"),
                HOST_ID,
                "삭제된 기기",
                BASE_TIME.plusSeconds(30),
                BASE_TIME.plusSeconds(300));
        insertDevice(
                UUID.fromString("30000000-0000-0000-0000-000000000003"),
                MEMBER_ID,
                "iPad",
                BASE_TIME.plusSeconds(60),
                null);
    }

    @Test
    void 활성_멤버십_존재_여부를_확인한다() {
        assertThat(sharedGroupMembershipRepository.existsActiveMembership(GROUP_ID, HOST_ID))
                .isTrue();
        assertThat(sharedGroupMembershipRepository.existsActiveMembership(GROUP_ID, STRANGER_ID))
                .isFalse();
        assertThat(
                        sharedGroupMembershipRepository.existsActiveMembership(
                                GROUP_ID, DELETED_USER_ID))
                .isFalse();
        assertThat(
                        sharedGroupMembershipRepository.existsActiveMembership(
                                DELETED_GROUP_ID, STRANGER_ID))
                .isFalse();
    }

    @Test
    void 활성_멤버만_joinedAt과_membershipId_오름차순으로_조회한다() {
        List<SharedGroupMemberRow> rows =
                sharedGroupMembershipRepository.findActiveMembers(GROUP_ID, PageRequest.of(0, 10));

        assertThat(rows)
                .extracting(SharedGroupMemberRow::userId)
                .containsExactly(HOST_ID, MEMBER_ID);
        assertThat(rows)
                .extracting(SharedGroupMemberRow::joinedAt)
                .containsExactly(BASE_TIME, BASE_TIME.plusSeconds(60));
    }

    @Test
    void 커서_이후_멤버만_조회한다() {
        List<SharedGroupMemberRow> rows =
                sharedGroupMembershipRepository.findActiveMembersAfter(
                        GROUP_ID,
                        BASE_TIME,
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        PageRequest.of(0, 10));

        assertThat(rows).extracting(SharedGroupMemberRow::userId).containsExactly(MEMBER_ID);
    }

    @Test
    void 활성_기기만_사용자별_생성일과_id_오름차순으로_조회한다() {
        List<DeviceRow> rows =
                deviceRepository.findActiveDevicesByAppUserIds(List.of(HOST_ID, MEMBER_ID));

        assertThat(rows).extracting(DeviceRow::name).containsExactly("iPhone 16 Pro", "iPad");
    }

    private void insertUser(UUID id, String appleSubject, String displayName, Instant deletedAt) {
        jdbcTemplate.update(
                """
                insert into app_user (id, apple_subject, display_name, created_at, updated_at, deleted_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                id,
                appleSubject,
                displayName,
                timestamp(BASE_TIME),
                timestamp(BASE_TIME),
                timestamp(deletedAt));
    }

    private void insertInviteCode(String inviteCode) {
        jdbcTemplate.update(
                "insert into invite_code_reservation (invite_code, created_at) values (?, ?)",
                inviteCode,
                timestamp(BASE_TIME));
    }

    private void insertGroup(
            UUID id, UUID createdByAppUserId, String name, String inviteCode, Instant deletedAt) {
        jdbcTemplate.update(
                """
                insert into shared_group (
                    id, created_by_app_user_id, name, invite_code, created_at, updated_at, deleted_at
                )
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                createdByAppUserId,
                name,
                inviteCode,
                timestamp(BASE_TIME),
                timestamp(BASE_TIME),
                timestamp(deletedAt));
    }

    private void insertMembership(
            UUID id, UUID sharedGroupId, UUID appUserId, String role, Instant createdAt) {
        jdbcTemplate.update(
                """
                insert into shared_group_membership (
                    id, shared_group_id, app_user_id, role, created_at, updated_at
                )
                values (?, ?, ?, ?, ?, ?)
                """,
                id,
                sharedGroupId,
                appUserId,
                role,
                timestamp(createdAt),
                timestamp(createdAt));
    }

    private void insertDevice(
            UUID id, UUID appUserId, String name, Instant createdAt, Instant deletedAt) {
        jdbcTemplate.update(
                """
                insert into device (id, app_user_id, name, created_at, updated_at, deleted_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                id,
                appUserId,
                name,
                timestamp(createdAt),
                timestamp(createdAt),
                timestamp(deletedAt));
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
