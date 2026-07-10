package org.zipzip.zipzipserver.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.zipzip.zipzipserver.domain.reaction.repository.PhotoLikeRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupMembershipRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class UserProfileRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PhotoLikeRepository photoLikeRepository;
    @Autowired private SharedGroupMembershipRepository sharedGroupMembershipRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Test
    void 활성_사용자만_잠금_조회한다() {
        UUID activeUserId = insertAppUser();
        UUID withdrawnUserId = insertAppUser();
        jdbcTemplate.update("update app_user set deleted_at = now() where id = ?", withdrawnUserId);

        assertThat(appUserRepository.findWithLockByIdAndDeletedAtIsNull(activeUserId)).isPresent();
        assertThat(appUserRepository.findWithLockByIdAndDeletedAtIsNull(withdrawnUserId)).isEmpty();
    }

    @Test
    void 사용자_좋아요와_MEMBER_멤버십만_삭제한다() {
        UUID appUserId = insertAppUser();
        UUID sharedGroupId = UUID.randomUUID();
        String inviteCode = "invite-" + UUID.randomUUID();
        jdbcTemplate.update(
                "insert into invite_code_reservation (invite_code) values (?)", inviteCode);
        jdbcTemplate.update(
                "insert into shared_group (id, created_by_app_user_id, name, invite_code) values"
                        + " (?, ?, ?, ?)",
                sharedGroupId,
                appUserId,
                "공유 그룹",
                inviteCode);
        jdbcTemplate.update(
                "insert into shared_group_membership (id, shared_group_id, app_user_id, role)"
                        + " values (?, ?, ?, 'MEMBER')",
                UUID.randomUUID(),
                sharedGroupId,
                appUserId);

        UUID photoId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into photo (id, uploaded_by_app_user_id, original_object_key,"
                        + " thumbnail_status, is_inferred) values (?, ?, ?, 'PENDING', false)",
                photoId,
                appUserId,
                "photos/" + UUID.randomUUID() + "/original.jpg");
        jdbcTemplate.update(
                "insert into photo_like (id, photo_id, app_user_id) values (?, ?, ?)",
                UUID.randomUUID(),
                photoId,
                appUserId);

        photoLikeRepository.deleteByAppUserId(appUserId);
        sharedGroupMembershipRepository.deleteByAppUserIdAndRole(appUserId, SharedGroupRole.MEMBER);
        entityManager.flush();

        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from photo_like where app_user_id = ?",
                                Integer.class,
                                appUserId))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "select count(*) from shared_group_membership where app_user_id ="
                                        + " ?",
                                Integer.class,
                                appUserId))
                .isZero();
    }

    private UUID insertAppUser() {
        UUID appUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into app_user (id, apple_subject, display_name) values (?, ?, ?)",
                appUserId,
                "apple-subject-" + appUserId,
                "집집이");
        return appUserId;
    }
}
