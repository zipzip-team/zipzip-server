package org.zipzip.zipzipserver.domain.album.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import org.zipzip.zipzipserver.domain.storage.ObjectStorageService;
import org.zipzip.zipzipserver.domain.storage.PresignedDownload;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SharedGroupAlbumControllerIntegrationTest {

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

    @MockitoBean private ObjectStorageService objectStorageService;

    private AppUser creator;
    private SharedGroup sharedGroup;

    @BeforeEach
    void setUp() {
        creator = persist(AppUser.create("creator-" + UUID.randomUUID(), "생성자"));
        InviteCodeReservation inviteCode =
                persist(InviteCodeReservation.create("CODE" + UUID.randomUUID()));
        sharedGroup = persist(SharedGroup.create(creator, "그룹", inviteCode));
        persist(SharedGroupMembership.create(sharedGroup, creator, SharedGroupRole.HOST));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void 앨범_목록_조회시_사진이_찍힌_시각이_아니라_앨범에_먼저_추가된_순으로_최대_3장의_썸네일을_반환한다() throws Exception {
        SharedGroup managedGroup = entityManager.find(SharedGroup.class, sharedGroup.getId());
        AppUser managedCreator = entityManager.find(AppUser.class, creator.getId());
        SharedAlbum album = persist(SharedAlbum.create(managedGroup, managedCreator, "앨범"));

        // takenAt은 일부러 추가 순서와 반대로 둔다: 이 앨범에 가장 먼저 추가된 사진(firstAttached)이
        // 실제로는 가장 나중에 촬영된 사진이고, 가장 나중에 추가된(그래서 3장 제한에서 빠지는) 사진이
        // 가장 오래전에 촬영된 사진이다. 정렬 기준이 photo.takenAt이 아니라 앨범 추가 시각(SharedAlbumPhoto.createdAt)
        // 임을 검증한다.
        Photo firstAttached =
                aReadyPhoto(
                        managedCreator,
                        Instant.parse("2026-04-01T00:00:00Z"),
                        "thumb/first-attached.jpg");
        Photo secondAttached =
                aReadyPhoto(
                        managedCreator,
                        Instant.parse("2026-03-01T00:00:00Z"),
                        "thumb/second-attached.jpg");
        Photo thirdAttached =
                aReadyPhoto(
                        managedCreator,
                        Instant.parse("2026-02-01T00:00:00Z"),
                        "thumb/third-attached.jpg");
        Photo fourthAttachedOldestTaken =
                aReadyPhoto(
                        managedCreator,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        "thumb/fourth-attached.jpg");
        persist(firstAttached);
        persist(secondAttached);
        persist(thirdAttached);
        persist(fourthAttachedOldestTaken);
        entityManager.flush();

        persist(SharedAlbumPhoto.create(album, firstAttached));
        entityManager.flush();
        persist(SharedAlbumPhoto.create(album, secondAttached));
        entityManager.flush();
        persist(SharedAlbumPhoto.create(album, thirdAttached));
        entityManager.flush();
        persist(SharedAlbumPhoto.create(album, fourthAttachedOldestTaken));
        entityManager.flush();
        entityManager.clear();

        when(objectStorageService.issueDownloadUrl(eq("thumb/first-attached.jpg"), any()))
                .thenReturn(new PresignedDownload("https://cdn/first-attached", Instant.now()));
        when(objectStorageService.issueDownloadUrl(eq("thumb/second-attached.jpg"), any()))
                .thenReturn(new PresignedDownload("https://cdn/second-attached", Instant.now()));
        when(objectStorageService.issueDownloadUrl(eq("thumb/third-attached.jpg"), any()))
                .thenReturn(new PresignedDownload("https://cdn/third-attached", Instant.now()));

        mockMvc.perform(
                        get(
                                        "/api/v1/shared-groups/{sharedGroupId}/shared-albums",
                                        sharedGroup.getId())
                                .header("Authorization", bearerToken(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].thumbnails.length()").value(3))
                .andExpect(
                        jsonPath("$.data.items[0].thumbnails[0].url")
                                .value("https://cdn/first-attached"))
                .andExpect(
                        jsonPath("$.data.items[0].thumbnails[1].url")
                                .value("https://cdn/second-attached"))
                .andExpect(
                        jsonPath("$.data.items[0].thumbnails[2].url")
                                .value("https://cdn/third-attached"));
    }

    @Test
    void 먼저_추가된_사진의_썸네일이_준비되지_않으면_뒤의_준비된_사진으로_최대_3장을_채운다() throws Exception {
        SharedGroup managedGroup = entityManager.find(SharedGroup.class, sharedGroup.getId());
        AppUser managedCreator = entityManager.find(AppUser.class, creator.getId());
        SharedAlbum album = persist(SharedAlbum.create(managedGroup, managedCreator, "앨범"));

        Photo pending = aPhoto(managedCreator, Instant.parse("2026-01-01T00:00:00Z"));
        Photo failed = aPhoto(managedCreator, Instant.parse("2026-01-02T00:00:00Z"));
        failed.markThumbnailFailed();
        Photo anotherFailed = aPhoto(managedCreator, Instant.parse("2026-01-03T00:00:00Z"));
        anotherFailed.markThumbnailFailed();
        Photo readyFirst =
                aReadyPhoto(
                        managedCreator,
                        Instant.parse("2026-01-04T00:00:00Z"),
                        "thumb/ready-first.jpg");
        Photo readySecond =
                aReadyPhoto(
                        managedCreator,
                        Instant.parse("2026-01-05T00:00:00Z"),
                        "thumb/ready-second.jpg");
        Photo readyThird =
                aReadyPhoto(
                        managedCreator,
                        Instant.parse("2026-01-06T00:00:00Z"),
                        "thumb/ready-third.jpg");
        persist(pending);
        persist(failed);
        persist(anotherFailed);
        persist(readyFirst);
        persist(readySecond);
        persist(readyThird);
        entityManager.flush();

        persist(SharedAlbumPhoto.create(album, pending));
        entityManager.flush();
        persist(SharedAlbumPhoto.create(album, failed));
        entityManager.flush();
        persist(SharedAlbumPhoto.create(album, anotherFailed));
        entityManager.flush();
        persist(SharedAlbumPhoto.create(album, readyFirst));
        entityManager.flush();
        persist(SharedAlbumPhoto.create(album, readySecond));
        entityManager.flush();
        persist(SharedAlbumPhoto.create(album, readyThird));
        entityManager.flush();
        entityManager.clear();

        when(objectStorageService.issueDownloadUrl(eq("thumb/ready-first.jpg"), any()))
                .thenReturn(new PresignedDownload("https://cdn/ready-first", Instant.now()));
        when(objectStorageService.issueDownloadUrl(eq("thumb/ready-second.jpg"), any()))
                .thenReturn(new PresignedDownload("https://cdn/ready-second", Instant.now()));
        when(objectStorageService.issueDownloadUrl(eq("thumb/ready-third.jpg"), any()))
                .thenReturn(new PresignedDownload("https://cdn/ready-third", Instant.now()));

        mockMvc.perform(
                        get(
                                        "/api/v1/shared-groups/{sharedGroupId}/shared-albums",
                                        sharedGroup.getId())
                                .header("Authorization", bearerToken(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].thumbnails.length()").value(3))
                .andExpect(
                        jsonPath("$.data.items[0].thumbnails[0].url")
                                .value("https://cdn/ready-first"))
                .andExpect(
                        jsonPath("$.data.items[0].thumbnails[1].url")
                                .value("https://cdn/ready-second"))
                .andExpect(
                        jsonPath("$.data.items[0].thumbnails[2].url")
                                .value("https://cdn/ready-third"));
    }

    @Test
    void 사진이_없는_앨범은_빈_썸네일_배열을_반환한다() throws Exception {
        SharedGroup managedGroup = entityManager.find(SharedGroup.class, sharedGroup.getId());
        AppUser managedCreator = entityManager.find(AppUser.class, creator.getId());
        persist(SharedAlbum.create(managedGroup, managedCreator, "빈 앨범"));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(
                        get(
                                        "/api/v1/shared-groups/{sharedGroupId}/shared-albums",
                                        sharedGroup.getId())
                                .header("Authorization", bearerToken(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].thumbnails.length()").value(0));
    }

    private Photo aReadyPhoto(AppUser uploader, Instant takenAt, String thumbnailObjectKey) {
        Photo photo = aPhoto(uploader, takenAt);
        photo.markThumbnailReady(thumbnailObjectKey);
        return photo;
    }

    private Photo aPhoto(AppUser uploader, Instant takenAt) {
        return Photo.create(
                uploader,
                "iPhone 15",
                "photos/" + UUID.randomUUID() + "/original.jpg",
                takenAt,
                4032,
                3024);
    }

    private String bearerToken(AppUser appUser) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(appUser.getId());
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }
}
