package org.zipzip.zipzipserver.domain.sharedgroup.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.zipzip.zipzipserver.domain.auth.jwt.JwtTokenProvider;
import org.zipzip.zipzipserver.domain.sharedgroup.code.SharedGroupSuccessCode;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.InviteCodeResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupJoinResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;
import org.zipzip.zipzipserver.domain.sharedgroup.service.SharedGroupInviteService;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyResult;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyService;
import org.zipzip.zipzipserver.global.response.BaseResponse;
import org.zipzip.zipzipserver.global.security.JwtAuthenticationFilter;
import org.zipzip.zipzipserver.global.security.SecurityConfig;
import org.zipzip.zipzipserver.global.security.SecurityExceptionResponseWriter;

@WebMvcTest(
        controllers = SharedGroupInviteController.class,
        properties = "spring.config.import=optional:classpath:config/application-secret.yml")
@Import({
    SecurityConfig.class,
    JwtAuthenticationFilter.class,
    SecurityExceptionResponseWriter.class
})
class SharedGroupInviteControllerTest {

    private static final UUID SHARED_GROUP_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000000");
    private static final UUID CURRENT_USER_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000000");
    private static final String ACCESS_TOKEN = "valid-token";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private SharedGroupInviteService sharedGroupInviteService;
    @MockitoBean private IdempotencyService idempotencyService;

    @Test
    void 초대_코드_조회는_Authorization_헤더가_없으면_401을_응답한다() throws Exception {
        mockMvc.perform(get("/api/v1/shared-groups/{sharedGroupId}/invite-code", SHARED_GROUP_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 공유_그룹_참여는_Authorization_헤더가_없으면_401을_응답한다() throws Exception {
        mockMvc.perform(
                        post("/api/v1/shared-groups/join")
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"inviteCode\":\"ABC234EF\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 공유_그룹_탈퇴는_Authorization_헤더가_없으면_401을_응답한다() throws Exception {
        mockMvc.perform(delete("/api/v1/shared-groups/{sharedGroupId}/members/me", SHARED_GROUP_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 인증된_사용자는_초대_코드를_조회한다() throws Exception {
        givenAuthenticatedUser();
        when(sharedGroupInviteService.findInviteCode(SHARED_GROUP_ID, CURRENT_USER_ID))
                .thenReturn(new InviteCodeResponse(SHARED_GROUP_ID, "ABC234EF"));

        mockMvc.perform(
                        get("/api/v1/shared-groups/{sharedGroupId}/invite-code", SHARED_GROUP_ID)
                                .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INVITE_CODE_FOUND"))
                .andExpect(jsonPath("$.data.inviteCode").value("ABC234EF"));

        verify(sharedGroupInviteService).findInviteCode(SHARED_GROUP_ID, CURRENT_USER_ID);
    }

    @Test
    void 인증된_사용자는_초대_코드로_공유_그룹에_참여한다() throws Exception {
        UUID idempotencyKey = UUID.randomUUID();
        givenAuthenticatedUser();
        BaseResponse<SharedGroupJoinResponse> responseBody =
                BaseResponse.success(
                        SharedGroupSuccessCode.SHARED_GROUP_JOINED,
                        new SharedGroupJoinResponse(
                                SHARED_GROUP_ID,
                                "우리 집",
                                SharedGroupRole.MEMBER,
                                Instant.parse("2026-07-10T00:00:00Z")));
        when(idempotencyService.execute(
                        eq(CURRENT_USER_ID.toString()),
                        eq(idempotencyKey),
                        eq("POST"),
                        eq("/api/v1/shared-groups/join"),
                        any(),
                        any()))
                .thenReturn(
                        new IdempotencyResult(HttpStatusCode.valueOf(201), responseBody, false));

        mockMvc.perform(
                        post("/api/v1/shared-groups/join")
                                .header("Authorization", bearerToken())
                                .header("Idempotency-Key", idempotencyKey.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"inviteCode\":\"ABC234EF\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SHARED_GROUP_JOINED"))
                .andExpect(jsonPath("$.data.sharedGroupId").value(SHARED_GROUP_ID.toString()))
                .andExpect(jsonPath("$.data.myRole").value("MEMBER"));
    }

    @Test
    void 인증된_사용자는_공유_그룹에서_탈퇴한다() throws Exception {
        givenAuthenticatedUser();

        mockMvc.perform(
                        delete("/api/v1/shared-groups/{sharedGroupId}/members/me", SHARED_GROUP_ID)
                                .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SHARED_GROUP_LEFT"));

        verify(sharedGroupInviteService).leave(SHARED_GROUP_ID, CURRENT_USER_ID);
    }

    private void givenAuthenticatedUser() {
        when(jwtTokenProvider.verifyAccessToken(ACCESS_TOKEN))
                .thenReturn(new JwtTokenProvider.AccessTokenClaims(CURRENT_USER_ID));
    }

    private String bearerToken() {
        return "Bearer " + ACCESS_TOKEN;
    }
}
