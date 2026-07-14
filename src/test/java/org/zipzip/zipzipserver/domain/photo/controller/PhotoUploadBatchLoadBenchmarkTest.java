package org.zipzip.zipzipserver.domain.photo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbum;
import org.zipzip.zipzipserver.domain.auth.jwt.JwtTokenProvider;
import org.zipzip.zipzipserver.domain.photo.entity.PhotoUploadReservation;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.InviteCodeReservation;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupMembership;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;
import org.zipzip.zipzipserver.domain.storage.ObjectStorageService;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;

/**
 * 이슈 #70 ADR API-14(PHOTO-02/03 배치 크기 20)의 "서버 인스턴스 성능 미반영" 지적을 검증하기 위한 조사용 벤치마크. 배치 크기(5/10/20)별
 * 완료 등록(PHOTO-03) 트랜잭션 소요 시간과, 동시 여러 사용자가 배치를 완료 등록할 때 실제 {@code thumbnailExecutor} 스레드풀(4~6개, 큐
 * 100)이 어떻게 반응하는지를 관찰한다.
 *
 * <p>Object Storage 호출({@code exists}/{@code download})에는 실제 네트워크 왕복을 흉내 내는 인위적 지연을 준다. 정확한
 * pass/fail 기준이 있는 회귀 테스트가 아니라 수치를 남기기 위한 도구이므로 기본 {@code test} 태스크 실행에서 제외한다({@code build.gradle}의
 * {@code excludeTags 'benchmark'} 참고). 실행: {@code ./gradlew test --tests
 * "*PhotoUploadBatchLoadBenchmarkTest" -PincludeBenchmark}.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class PhotoUploadBatchLoadBenchmarkTest {

    private static final int SIMULATED_STORAGE_LATENCY_MILLIS = 80;

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

    @Autowired
    @Qualifier("thumbnailExecutor")
    private java.util.concurrent.Executor thumbnailExecutor;

    @MockitoBean private ObjectStorageService objectStorageService;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private SharedGroup sharedGroup;
    private SharedAlbum sharedAlbum;
    private byte[] sampleJpeg;

    @BeforeEach
    void setUp() throws Exception {
        transactionTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(
                status -> {
                    AppUser host =
                            persist(AppUser.create("bench-host-" + UUID.randomUUID(), "벤치호스트"));
                    InviteCodeReservation inviteCode =
                            persist(InviteCodeReservation.create("CODE" + UUID.randomUUID()));
                    sharedGroup = persist(SharedGroup.create(host, "벤치그룹", inviteCode));
                    persist(SharedGroupMembership.create(sharedGroup, host, SharedGroupRole.HOST));
                    sharedAlbum = persist(SharedAlbum.create(sharedGroup, host, "벤치앨범"));
                    entityManager.flush();
                });
        entityManager.clear();

        sampleJpeg = smallJpeg();
        when(objectStorageService.exists(anyString()))
                .thenAnswer(
                        invocation -> {
                            Thread.sleep(SIMULATED_STORAGE_LATENCY_MILLIS);
                            return true;
                        });
        when(objectStorageService.download(anyString()))
                .thenAnswer(
                        invocation -> {
                            Thread.sleep(SIMULATED_STORAGE_LATENCY_MILLIS);
                            return sampleJpeg;
                        });
        when(objectStorageService.issueDownloadUrl(anyString(), any()))
                .thenReturn(
                        new org.zipzip.zipzipserver.domain.storage.PresignedDownload(
                                "https://bench.example/original", Instant.now().plusSeconds(600)));
    }

    @Test
    @Tag("benchmark")
    void 배치_크기별_완료등록_소요시간을_측정한다() throws Exception {
        AppUser uploader =
                transactionTemplate.execute(
                        status -> {
                            AppUser created =
                                    persist(
                                            AppUser.create(
                                                    "bench-single-" + UUID.randomUUID(), "벤치업로더"));
                            persist(
                                    SharedGroupMembership.create(
                                            sharedGroup, created, SharedGroupRole.MEMBER));
                            entityManager.flush();
                            return created;
                        });
        entityManager.clear();

        System.out.println(
                "\n=== 배치 크기별 완료 등록(PHOTO-03) 소요 시간 (단일 요청, Object Storage 지연 80ms) ===");
        for (int batchSize : new int[] {5, 10, 20}) {
            List<String> objectKeys = createReservations(batchSize, sharedAlbum, uploader);
            String requestBody = completeRequestBody(objectKeys);

            long start = System.nanoTime();
            mockMvc.perform(
                            post(
                                            "/api/v1/shared-albums/{sharedAlbumId}/photos/complete",
                                            sharedAlbum.getId())
                                    .header("Authorization", bearerToken(uploader))
                                    .header("Idempotency-Key", UUID.randomUUID().toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
                    .andExpect(status().isCreated());
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            System.out.printf(
                    "배치=%2d개  소요시간=%4dms  (파일당 평균 %.1fms)%n",
                    batchSize, elapsedMs, elapsedMs / (double) batchSize);
        }
    }

    @Test
    @Tag("benchmark")
    void 동시_여러_사용자가_배치를_완료등록할_때_지연과_썸네일_큐_상태를_관찰한다() throws Exception {
        int concurrentUsers = 3;
        int batchSize = 20;

        List<AppUser> uploaders = new ArrayList<>();
        List<String> requestBodies = new ArrayList<>();
        for (int i = 0; i < concurrentUsers; i++) {
            int index = i;
            AppUser uploader =
                    transactionTemplate.execute(
                            status -> {
                                AppUser created =
                                        persist(
                                                AppUser.create(
                                                        "bench-concurrent-" + UUID.randomUUID(),
                                                        "벤치유저" + index));
                                persist(
                                        SharedGroupMembership.create(
                                                sharedGroup, created, SharedGroupRole.MEMBER));
                                entityManager.flush();
                                return created;
                            });
            uploaders.add(uploader);
        }
        entityManager.clear();
        for (AppUser uploader : uploaders) {
            List<String> objectKeys = createReservations(batchSize, sharedAlbum, uploader);
            requestBodies.add(completeRequestBody(objectKeys));
        }

        ExecutorService clientPool = Executors.newFixedThreadPool(concurrentUsers);
        List<Future<Long>> futures = new ArrayList<>();
        long overallStart = System.nanoTime();
        for (int i = 0; i < concurrentUsers; i++) {
            AppUser uploader = uploaders.get(i);
            String requestBody = requestBodies.get(i);
            futures.add(
                    clientPool.submit(
                            () -> {
                                long start = System.nanoTime();
                                mockMvc.perform(
                                                post(
                                                                "/api/v1/shared-albums/{sharedAlbumId}/photos/complete",
                                                                sharedAlbum.getId())
                                                        .header(
                                                                "Authorization",
                                                                bearerToken(uploader))
                                                        .header(
                                                                "Idempotency-Key",
                                                                UUID.randomUUID().toString())
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(requestBody))
                                        .andExpect(status().isCreated());
                                return (System.nanoTime() - start) / 1_000_000;
                            }));
        }

        List<Long> latencies = new ArrayList<>();
        for (Future<Long> future : futures) {
            latencies.add(future.get());
        }
        long overallElapsedMs = (System.nanoTime() - overallStart) / 1_000_000;
        clientPool.shutdown();

        Thread.sleep(50);
        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) thumbnailExecutor;

        System.out.println("\n=== 동시 사용자 완료 등록 시 지연·썸네일 큐 상태 ===");
        System.out.printf(
                "동시 사용자 %d명 x 배치 %d개 → 전체 소요=%dms, 개별 요청 소요=%s%n",
                concurrentUsers, batchSize, overallElapsedMs, latencies);
        System.out.printf(
                "직후 thumbnailExecutor 활성 스레드=%d, 대기 큐=%d (설정: core=%d max=%d)%n",
                pool.getActiveCount(),
                pool.getThreadPoolExecutor().getQueue().size(),
                pool.getCorePoolSize(),
                pool.getMaxPoolSize());
    }

    private List<String> createReservations(int count, SharedAlbum album, AppUser uploader) {
        List<String> objectKeys =
                transactionTemplate.execute(
                        status -> {
                            SharedAlbum managedAlbum =
                                    entityManager.find(SharedAlbum.class, album.getId());
                            AppUser managedUploader =
                                    entityManager.find(AppUser.class, uploader.getId());
                            Instant expiresAt = Instant.now().plusSeconds(900);
                            List<String> keys = new ArrayList<>();
                            for (int i = 0; i < count; i++) {
                                String objectKey = "bench/" + UUID.randomUUID() + ".jpg";
                                persist(
                                        PhotoUploadReservation.create(
                                                objectKey,
                                                managedAlbum,
                                                managedUploader,
                                                expiresAt));
                                keys.add(objectKey);
                            }
                            entityManager.flush();
                            return keys;
                        });
        entityManager.clear();
        return objectKeys;
    }

    private String completeRequestBody(List<String> objectKeys) throws Exception {
        List<Map<String, Object>> files = new ArrayList<>();
        for (String objectKey : objectKeys) {
            files.add(Map.of("objectKey", objectKey));
        }
        return objectMapper.writeValueAsString(Map.of("files", files));
    }

    private byte[] smallJpeg() throws Exception {
        BufferedImage image = new BufferedImage(200, 150, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.GRAY);
        graphics.fillRect(0, 0, 200, 150);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return output.toByteArray();
    }

    private String bearerToken(AppUser appUser) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(appUser.getId());
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }
}
