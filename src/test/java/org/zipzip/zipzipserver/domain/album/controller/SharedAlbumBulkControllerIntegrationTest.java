package org.zipzip.zipzipserver.domain.album.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbum;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbumPhoto;
import org.zipzip.zipzipserver.domain.auth.jwt.JwtTokenProvider;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.InviteCodeReservation;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupMembership;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SharedAlbumBulkControllerIntegrationTest {

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

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager entityManager;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private AppUser creator;
    private AppUser member;
    private SharedGroup sharedGroup;

    @BeforeEach
    void setUp() {
        creator = persist(AppUser.create("creator-" + UUID.randomUUID(), "생성자"));
        member = persist(AppUser.create("member-" + UUID.randomUUID(), "멤버"));
        InviteCodeReservation inviteCode =
                persist(InviteCodeReservation.create("CODE" + UUID.randomUUID()));
        sharedGroup = persist(SharedGroup.create(creator, "그룹", inviteCode));
        persist(SharedGroupMembership.create(sharedGroup, creator, SharedGroupRole.HOST));
        persist(SharedGroupMembership.create(sharedGroup, member, SharedGroupRole.MEMBER));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void 생성자가_아닌_활성_멤버도_여러_앨범을_일괄_삭제하고_마지막_소속_사진도_함께_삭제된다() throws Exception {
        SharedGroup managedGroup = entityManager.find(SharedGroup.class, sharedGroup.getId());
        AppUser managedCreator = entityManager.find(AppUser.class, creator.getId());
        SharedAlbum albumA = persist(SharedAlbum.create(managedGroup, managedCreator, "앨범 A"));
        SharedAlbum albumB = persist(SharedAlbum.create(managedGroup, managedCreator, "앨범 B"));
        Photo sharedAcrossTargets =
                persist(
                        Photo.create(
                                managedCreator,
                                "iPhone 15",
                                "photos/" + UUID.randomUUID() + "/original.jpg",
                                Instant.parse("2026-06-30T04:20:00Z"),
                                4032,
                                3024));
        persist(SharedAlbumPhoto.create(albumA, sharedAcrossTargets));
        persist(SharedAlbumPhoto.create(albumB, sharedAcrossTargets));
        entityManager.flush();
        entityManager.clear();

        String requestBody =
                """
                {"sharedAlbumIds": ["%s", "%s"]}
                """
                        .formatted(albumA.getId(), albumB.getId());

        mockMvc.perform(
                        post("/api/v1/shared-albums/bulk-delete")
                                .header("Authorization", bearerToken(member))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SHARED_ALBUMS_DELETED"))
                .andExpect(jsonPath("$.data.deletedAlbumCount").value(2))
                .andExpect(jsonPath("$.data.deletedPhotoCount").value(1));

        entityManager.clear();
        SharedAlbum persistedAlbumA = entityManager.find(SharedAlbum.class, albumA.getId());
        SharedAlbum persistedAlbumB = entityManager.find(SharedAlbum.class, albumB.getId());
        Photo persistedPhoto = entityManager.find(Photo.class, sharedAcrossTargets.getId());
        assertThat(persistedAlbumA.getDeletedAt()).isNotNull();
        assertThat(persistedAlbumB.getDeletedAt()).isNotNull();
        assertThat(persistedPhoto.getDeletedAt()).isNotNull();
    }

    @Test
    void 같은_Idempotency_Key로_재요청하면_같은_응답을_재전송하고_다시_삭제하지_않는다() throws Exception {
        SharedGroup managedGroup = entityManager.find(SharedGroup.class, sharedGroup.getId());
        AppUser managedCreator = entityManager.find(AppUser.class, creator.getId());
        SharedAlbum album = persist(SharedAlbum.create(managedGroup, managedCreator, "앨범"));
        entityManager.flush();
        entityManager.clear();

        String idempotencyKey = UUID.randomUUID().toString();
        String requestBody =
                """
                {"sharedAlbumIds": ["%s"]}
                """
                        .formatted(album.getId());

        mockMvc.perform(
                        post("/api/v1/shared-albums/bulk-delete")
                                .header("Authorization", bearerToken(creator))
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedAlbumCount").value(1));

        mockMvc.perform(
                        post("/api/v1/shared-albums/bulk-delete")
                                .header("Authorization", bearerToken(creator))
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.data.deletedAlbumCount").value(1));
    }

    @Test
    void 대상_중_하나라도_존재하지_않으면_전체가_실패하고_다른_앨범도_삭제되지_않는다() throws Exception {
        SharedGroup managedGroup = entityManager.find(SharedGroup.class, sharedGroup.getId());
        AppUser managedCreator = entityManager.find(AppUser.class, creator.getId());
        SharedAlbum existingAlbum = persist(SharedAlbum.create(managedGroup, managedCreator, "앨범"));
        entityManager.flush();
        entityManager.clear();

        String requestBody =
                """
                {"sharedAlbumIds": ["%s", "%s"]}
                """
                        .formatted(existingAlbum.getId(), UUID.randomUUID());

        mockMvc.perform(
                        post("/api/v1/shared-albums/bulk-delete")
                                .header("Authorization", bearerToken(creator))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARED_ALBUM_NOT_FOUND"));

        entityManager.clear();
        SharedAlbum persistedAlbum = entityManager.find(SharedAlbum.class, existingAlbum.getId());
        assertThat(persistedAlbum.getDeletedAt()).isNull();
    }

    @Test
    void 요청_목록이_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(
                        post("/api/v1/shared-albums/bulk-delete")
                                .header("Authorization", bearerToken(creator))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"sharedAlbumIds": []}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SHARED_ALBUM_IDS"));
    }

    private String bearerToken(AppUser appUser) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(appUser.getId());
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }
}
