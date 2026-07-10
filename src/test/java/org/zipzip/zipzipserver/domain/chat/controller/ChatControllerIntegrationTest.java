package org.zipzip.zipzipserver.domain.chat.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbum;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbumPhoto;
import org.zipzip.zipzipserver.domain.auth.jwt.JwtTokenProvider;
import org.zipzip.zipzipserver.domain.chat.entity.SharedGroupChatMessage;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.reaction.entity.PhotoComment;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.InviteCodeReservation;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupMembership;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ChatControllerIntegrationTest {

    private static final UUID IDEMPOTENCY_KEY =
            UUID.fromString("54cf8d7e-a23e-4e76-90f7-603f122b1507");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
                .cleanDisabled(false)
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .clean();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void Authorization_헤더가_없으면_채팅_타임라인을_거부한다() throws Exception {
        mockMvc.perform(get("/api/v1/shared-groups/%s/chat-messages".formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 유효한_Bearer_토큰으로_일반_메시지와_사진_댓글을_함께_조회한다() throws Exception {
        ChatFixture fixture = persistFixture();

        mockMvc.perform(
                        get("/api/v1/shared-groups/%s/chat-messages"
                                        .formatted(fixture.sharedGroupId()))
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(fixture.memberId()))
                                .queryParam("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("SHARED_GROUP_CHAT_TIMELINE_FOUND"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[*].type", hasItem("CHAT_MESSAGE")))
                .andExpect(jsonPath("$.data.items[*].type", hasItem("PHOTO_COMMENT")))
                .andExpect(
                        jsonPath("$.data.items[?(@.type == 'PHOTO_COMMENT')].photoId")
                                .value(hasItem(fixture.photoId().toString())));
    }

    @Test
    void 다음_cursor로_조회하면_첫_페이지와_겹치지_않는_다음_타임라인_항목을_반환한다() throws Exception {
        ChatFixture fixture = persistFixture();
        String path = "/api/v1/shared-groups/%s/chat-messages".formatted(fixture.sharedGroupId());
        String authorization = bearerToken(fixture.memberId());

        String firstResponse =
                mockMvc.perform(
                                get(path)
                                        .header(HttpHeaders.AUTHORIZATION, authorization)
                                        .queryParam("size", "1"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.items.length()").value(1))
                        .andExpect(jsonPath("$.data.nextCursor", notNullValue()))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String firstItemId = JsonPath.read(firstResponse, "$.data.items[0].id");
        String nextCursor = JsonPath.read(firstResponse, "$.data.nextCursor");

        mockMvc.perform(
                        get(path)
                                .header(HttpHeaders.AUTHORIZATION, authorization)
                                .queryParam("size", "1")
                                .queryParam("cursor", nextCursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(
                        jsonPath("$.data.items[0].id")
                                .value(org.hamcrest.Matchers.not(firstItemId)))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void 다른_공유_그룹_사용자의_유효한_Bearer_토큰은_채팅_조회에_404를_반환한다() throws Exception {
        ChatFixture fixture = persistFixture();

        mockMvc.perform(
                        get("/api/v1/shared-groups/%s/chat-messages"
                                        .formatted(fixture.sharedGroupId()))
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearerToken(fixture.strangerId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("SHARED_GROUP_NOT_FOUND"));
    }

    @Test
    void 유효한_Bearer_토큰으로_메시지를_작성하고_같은_멱등성_키는_재전송한다() throws Exception {
        ChatFixture fixture = persistFixture();
        String path = "/api/v1/shared-groups/%s/chat-messages".formatted(fixture.sharedGroupId());
        String authorization = bearerToken(fixture.memberId());
        String requestBody = "{\"content\":\" 여행 사진 더 올릴게! \"}";

        mockMvc.perform(
                        post(path)
                                .header(HttpHeaders.AUTHORIZATION, authorization)
                                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.code").value("SHARED_GROUP_CHAT_MESSAGE_CREATED"))
                .andExpect(jsonPath("$.data.content").value("여행 사진 더 올릴게!"));

        mockMvc.perform(
                        post(path)
                                .header(HttpHeaders.AUTHORIZATION, authorization)
                                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.data.content").value("여행 사진 더 올릴게!"));

        Integer messageCount =
                jdbcTemplate.queryForObject(
                        "select count(*) from shared_group_chat_message where shared_group_id = ?",
                        Integer.class,
                        fixture.sharedGroupId());
        org.assertj.core.api.Assertions.assertThat(messageCount).isEqualTo(2);
    }

    private ChatFixture persistFixture() {
        return new TransactionTemplate(transactionManager)
                .execute(
                        status -> {
                            AppUser member = AppUser.create("member-" + UUID.randomUUID(), "집집이");
                            AppUser stranger =
                                    AppUser.create("stranger-" + UUID.randomUUID(), "다른 사용자");
                            entityManager.persist(member);
                            entityManager.persist(stranger);

                            InviteCodeReservation inviteCodeReservation =
                                    InviteCodeReservation.create(
                                            "INV"
                                                    + UUID.randomUUID()
                                                            .toString()
                                                            .replace("-", "")
                                                            .substring(0, 12));
                            entityManager.persist(inviteCodeReservation);
                            SharedGroup sharedGroup =
                                    SharedGroup.create(member, "여름 여행", inviteCodeReservation);
                            entityManager.persist(sharedGroup);
                            entityManager.persist(
                                    SharedGroupMembership.create(
                                            sharedGroup, member, SharedGroupRole.HOST));

                            SharedAlbum sharedAlbum =
                                    SharedAlbum.create(sharedGroup, member, "여행 사진");
                            entityManager.persist(sharedAlbum);
                            Photo photo =
                                    Photo.create(
                                            member,
                                            "iPhone 15",
                                            "photos/" + UUID.randomUUID() + "/original.jpg",
                                            null,
                                            1080,
                                            1920);
                            entityManager.persist(photo);
                            entityManager.persist(SharedAlbumPhoto.create(sharedAlbum, photo));
                            entityManager.persist(
                                    SharedGroupChatMessage.create(
                                            sharedGroup, member, "이번 여행 사진 올려줘!"));
                            entityManager.persist(PhotoComment.create(photo, member, "사진 너무 좋다!"));
                            entityManager.flush();

                            return new ChatFixture(
                                    member.getId(),
                                    stranger.getId(),
                                    sharedGroup.getId(),
                                    photo.getId());
                        });
    }

    private String bearerToken(UUID appUserId) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(appUserId);
    }

    private record ChatFixture(UUID memberId, UUID strangerId, UUID sharedGroupId, UUID photoId) {}
}
