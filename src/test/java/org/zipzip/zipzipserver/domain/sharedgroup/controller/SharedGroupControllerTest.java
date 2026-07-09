package org.zipzip.zipzipserver.domain.sharedgroup.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.zipzip.zipzipserver.domain.auth.jwt.JwtTokenProvider;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupListResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;
import org.zipzip.zipzipserver.domain.sharedgroup.service.SharedGroupService;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyResult;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyService;
import org.zipzip.zipzipserver.global.response.BaseResponse;
import org.zipzip.zipzipserver.global.security.JwtAuthenticationFilter;
import org.zipzip.zipzipserver.global.security.SecurityConfig;
import org.zipzip.zipzipserver.global.security.SecurityExceptionResponseWriter;

@WebMvcTest(
        value = SharedGroupController.class,
        properties = "spring.config.import=optional:classpath:config/application-secret.yml")
@Import({
    SecurityConfig.class,
    JwtAuthenticationFilter.class,
    SecurityExceptionResponseWriter.class
})
class SharedGroupControllerTest {

    private static final UUID APP_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String ACCESS_TOKEN = "access-token";

    @Autowired private MockMvc mockMvc;

    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private SharedGroupService sharedGroupService;
    @MockBean private IdempotencyService idempotencyService;

    @Test
    void 인증_토큰이_없으면_UNAUTHORIZED를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/shared-groups"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 내_공유_그룹_목록을_조회한다() throws Exception {
        givenAuthenticatedUser();
        when(sharedGroupService.findMySharedGroups(APP_USER_ID, null, null))
                .thenReturn(new SharedGroupListResponse(List.of(), null, false));

        mockMvc.perform(get("/api/v1/shared-groups").header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SHARED_GROUP_LIST_FOUND"))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 멱등성_replay_응답이면_헤더를_반환한다() throws Exception {
        givenAuthenticatedUser();
        BaseResponse<Object> replayedBody =
                new BaseResponse<>(
                        201,
                        "SHARED_GROUP_CREATED",
                        "공유 그룹을 생성했습니다.",
                        java.util.Map.of(
                                "id",
                                "22222222-2222-2222-2222-222222222222",
                                "name",
                                "우리 집",
                                "inviteCode",
                                "ABC234EF",
                                "myRole",
                                SharedGroupRole.HOST.name(),
                                "createdAt",
                                Instant.parse("2026-07-10T00:00:00Z").toString()));
        when(idempotencyService.execute(
                        eq(APP_USER_ID.toString()),
                        any(UUID.class),
                        eq("POST"),
                        eq("/api/v1/shared-groups"),
                        any(),
                        any()))
                .thenReturn(new IdempotencyResult(HttpStatusCode.valueOf(201), replayedBody, true));

        mockMvc.perform(
                        post("/api/v1/shared-groups")
                                .header("Authorization", bearerToken())
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"우리 집\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.code").value("SHARED_GROUP_CREATED"));
    }

    @Test
    void 잘못된_멱등성_key는_INVALID_REQUEST를_반환한다() throws Exception {
        givenAuthenticatedUser();

        mockMvc.perform(
                        post("/api/v1/shared-groups")
                                .header("Authorization", bearerToken())
                                .header("Idempotency-Key", "not-uuid")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"우리 집\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private void givenAuthenticatedUser() {
        when(jwtTokenProvider.verifyAccessToken(ACCESS_TOKEN))
                .thenReturn(new JwtTokenProvider.AccessTokenClaims(APP_USER_ID));
    }

    private String bearerToken() {
        return "Bearer " + ACCESS_TOKEN;
    }
}
