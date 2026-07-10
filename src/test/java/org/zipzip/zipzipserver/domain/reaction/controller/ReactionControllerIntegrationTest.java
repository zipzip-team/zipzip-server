package org.zipzip.zipzipserver.domain.reaction.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import org.zipzip.zipzipserver.domain.reaction.entity.PhotoComment;
import org.zipzip.zipzipserver.domain.reaction.entity.PhotoLike;
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
class ReactionControllerIntegrationTest {

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
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    @MockBean private ObjectStorageService objectStorageService;

    private AppUser uploader;
    private AppUser member;
    private Photo photo;

    @BeforeEach
    void setUp() {
        when(objectStorageService.issueDownloadUrl(anyString(), any()))
                .thenReturn(
                        new PresignedDownload(
                                "https://storage.example/presigned-image",
                                Instant.parse("2026-07-10T01:10:00Z")));

        uploader = persist(AppUser.create("uploader-" + UUID.randomUUID(), "업로더"));
        member = persist(AppUser.create("member-" + UUID.randomUUID(), "멤버"));
        InviteCodeReservation inviteCode =
                persist(InviteCodeReservation.create("CODE" + UUID.randomUUID()));
        SharedGroup sharedGroup = persist(SharedGroup.create(uploader, "그룹", inviteCode));
        persist(SharedGroupMembership.create(sharedGroup, uploader, SharedGroupRole.HOST));
        persist(SharedGroupMembership.create(sharedGroup, member, SharedGroupRole.MEMBER));
        SharedAlbum sharedAlbum = persist(SharedAlbum.create(sharedGroup, uploader, "앨범"));
        photo =
                persist(
                        Photo.create(
                                uploader,
                                "iPhone 15",
                                "photos/" + UUID.randomUUID() + "/original.jpg",
                                Instant.parse("2026-06-30T04:20:00Z"),
                                4032,
                                3024));
        persist(SharedAlbumPhoto.create(sharedAlbum, photo));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void 사진_상세_조회는_권한_검증후_메타데이터와_반응_요약을_반환한다() throws Exception {
        Photo managedPhoto = entityManager.find(Photo.class, photo.getId());
        AppUser managedMember = entityManager.find(AppUser.class, member.getId());
        persist(PhotoLike.create(managedPhoto, managedMember));
        persist(PhotoComment.create(managedPhoto, managedMember, "멋진 사진입니다."));
        entityManager.flush();

        mockMvc.perform(
                        get("/api/v1/photos/{photoId}", photo.getId())
                                .header("Authorization", bearerToken(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PHOTO_FOUND"))
                .andExpect(jsonPath("$.data.id").value(photo.getId().toString()))
                .andExpect(
                        jsonPath("$.data.originalUrl")
                                .value("https://storage.example/presigned-image"))
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.commentCount").value(1))
                .andExpect(jsonPath("$.data.isLikedByMe").value(true));
    }

    @Test
    void 좋아요_설정과_취소는_멱등하게_현재_상태를_반환한다() throws Exception {
        mockMvc.perform(
                        put("/api/v1/photos/{photoId}/like", photo.getId())
                                .header("Authorization", bearerToken(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PHOTO_LIKED"))
                .andExpect(jsonPath("$.data.isLikedByMe").value(true))
                .andExpect(jsonPath("$.data.likeCount").value(1));

        mockMvc.perform(
                        put("/api/v1/photos/{photoId}/like", photo.getId())
                                .header("Authorization", bearerToken(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(1));

        mockMvc.perform(
                        delete("/api/v1/photos/{photoId}/like", photo.getId())
                                .header("Authorization", bearerToken(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PHOTO_UNLIKED"))
                .andExpect(jsonPath("$.data.isLikedByMe").value(false))
                .andExpect(jsonPath("$.data.likeCount").value(0));
    }

    @Test
    void 댓글_작성_재시도는_같은_응답을_재전송하고_목록에서_조회된다() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String requestBody =
                objectMapper.writeValueAsString(java.util.Map.of("content", "사진 너무 좋다!"));

        mockMvc.perform(
                        post("/api/v1/photos/{photoId}/comments", photo.getId())
                                .header("Authorization", bearerToken(member))
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("PHOTO_COMMENT_CREATED"))
                .andExpect(jsonPath("$.data.content").value("사진 너무 좋다!"));

        mockMvc.perform(
                        post("/api/v1/photos/{photoId}/comments", photo.getId())
                                .header("Authorization", bearerToken(member))
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.data.content").value("사진 너무 좋다!"));

        mockMvc.perform(
                        get("/api/v1/photos/{photoId}/comments", photo.getId())
                                .header("Authorization", bearerToken(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PHOTO_COMMENT_LIST_FOUND"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].content").value("사진 너무 좋다!"))
                .andExpect(jsonPath("$.data.items[0].isAuthor").value(true));
    }

    @Test
    void 댓글_목록은_실제_postgresql에서_cursor_다음_페이지를_조회한다() throws Exception {
        Photo managedPhoto = entityManager.find(Photo.class, photo.getId());
        AppUser managedMember = entityManager.find(AppUser.class, member.getId());
        persist(PhotoComment.create(managedPhoto, managedMember, "첫 댓글"));
        entityManager.flush();
        persist(PhotoComment.create(managedPhoto, managedMember, "둘째 댓글"));
        entityManager.flush();

        String firstPageBody =
                mockMvc.perform(
                                get("/api/v1/photos/{photoId}/comments", photo.getId())
                                        .param("size", "1")
                                        .header("Authorization", bearerToken(member)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.items.length()").value(1))
                        .andExpect(jsonPath("$.data.hasNext").value(true))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode firstPage = objectMapper.readTree(firstPageBody);
        String nextCursor = firstPage.at("/data/nextCursor").asText();

        mockMvc.perform(
                        get("/api/v1/photos/{photoId}/comments", photo.getId())
                                .param("size", "1")
                                .param("cursor", nextCursor)
                                .header("Authorization", bearerToken(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].content").value("둘째 댓글"))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 비활성_멤버는_사진_반응_api에_접근할_수_없다() throws Exception {
        AppUser outsider = persist(AppUser.create("outsider-" + UUID.randomUUID(), "외부인"));
        entityManager.flush();

        mockMvc.perform(
                        get("/api/v1/photos/{photoId}/comments", photo.getId())
                                .header("Authorization", bearerToken(outsider)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PHOTO_NOT_FOUND"));
    }

    private String bearerToken(AppUser appUser) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(appUser.getId());
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }
}
