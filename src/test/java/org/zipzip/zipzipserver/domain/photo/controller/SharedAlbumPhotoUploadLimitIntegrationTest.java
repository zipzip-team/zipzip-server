package org.zipzip.zipzipserver.domain.photo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbum;
import org.zipzip.zipzipserver.domain.auth.jwt.JwtTokenProvider;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.InviteCodeReservation;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupMembership;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;
import org.zipzip.zipzipserver.domain.storage.ObjectStorageService;
import org.zipzip.zipzipserver.domain.storage.PresignedUpload;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SharedAlbumPhotoUploadLimitIntegrationTest {

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

    @MockitoBean private ObjectStorageService objectStorageService;

    private AppUser uploader;
    private SharedAlbum sharedAlbum;

    @BeforeEach
    void setUp() {
        uploader = persist(AppUser.create("uploader-" + UUID.randomUUID(), "업로더"));
        InviteCodeReservation inviteCode =
                persist(InviteCodeReservation.create("CODE" + UUID.randomUUID()));
        SharedGroup sharedGroup = persist(SharedGroup.create(uploader, "그룹", inviteCode));
        persist(SharedGroupMembership.create(sharedGroup, uploader, SharedGroupRole.HOST));
        sharedAlbum = persist(SharedAlbum.create(sharedGroup, uploader, "앨범"));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void 업로드_URL_발급_요청이_20개를_초과하면_400_TOO_MANY_FILES() throws Exception {
        String requestBody = objectMapper.writeValueAsString(uploadUrlRequestBody(21, 1_000L));

        mockMvc.perform(
                        post("/api/v1/shared-albums/{sharedAlbumId}/photos/upload-urls", sharedAlbum.getId())
                                .header("Authorization", bearerToken(uploader))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_FILES"));
    }

    @Test
    void 업로드_URL_발급_요청의_파일_크기가_20MiB를_초과하면_413_FILE_TOO_LARGE() throws Exception {
        String requestBody = objectMapper.writeValueAsString(uploadUrlRequestBody(1, 21L * 1024 * 1024));

        mockMvc.perform(
                        post("/api/v1/shared-albums/{sharedAlbumId}/photos/upload-urls", sharedAlbum.getId())
                                .header("Authorization", bearerToken(uploader))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void 업로드_URL_발급_요청이_정확히_20개_20MiB이면_200으로_20개_URL을_반환한다() throws Exception {
        when(objectStorageService.issueUploadUrl(anyString(), anyString(), anyLong(), any()))
                .thenReturn(
                        new PresignedUpload("https://upload-url", Instant.now().plusSeconds(900)));
        String requestBody =
                objectMapper.writeValueAsString(uploadUrlRequestBody(20, 20L * 1024 * 1024));

        mockMvc.perform(
                        post("/api/v1/shared-albums/{sharedAlbumId}/photos/upload-urls", sharedAlbum.getId())
                                .header("Authorization", bearerToken(uploader))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PHOTO_UPLOAD_URLS_ISSUED"))
                .andExpect(jsonPath("$.data.uploads.length()").value(20));
    }

    @Test
    void 완료_등록_요청이_20개를_초과하면_400_INVALID_UPLOAD_METADATA() throws Exception {
        List<Map<String, Object>> files = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            files.add(Map.of("objectKey", "object-key-" + i));
        }
        String requestBody = objectMapper.writeValueAsString(Map.of("files", files));

        mockMvc.perform(
                        post("/api/v1/shared-albums/{sharedAlbumId}/photos/complete", sharedAlbum.getId())
                                .header("Authorization", bearerToken(uploader))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_UPLOAD_METADATA"));
    }

    private Map<String, Object> uploadUrlRequestBody(int fileCount, long sizeBytes) {
        List<Map<String, Object>> files = new ArrayList<>();
        for (int i = 0; i < fileCount; i++) {
            files.add(Map.of("contentType", "image/jpeg", "sizeBytes", sizeBytes));
        }
        return Map.of("files", files);
    }

    private String bearerToken(AppUser appUser) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(appUser.getId());
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }
}
