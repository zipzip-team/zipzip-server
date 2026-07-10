package org.zipzip.zipzipserver.domain.sharedgroup.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.zipzip.zipzipserver.domain.auth.jwt.JwtTokenProvider;
import org.zipzip.zipzipserver.domain.sharedgroup.code.SharedGroupErrorCode;
import org.zipzip.zipzipserver.domain.sharedgroup.code.SharedGroupSuccessCode;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.CreateSharedGroupResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupListResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupUpdateResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;
import org.zipzip.zipzipserver.domain.sharedgroup.service.SharedGroupService;
import org.zipzip.zipzipserver.global.exception.BusinessException;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyService;
import org.zipzip.zipzipserver.global.security.JwtAuthenticationFilter;
import org.zipzip.zipzipserver.global.security.SecurityConfig;
import org.zipzip.zipzipserver.global.security.JwtAuthenticationEntryPoint;

@WebMvcTest(
        value = SharedGroupController.class,
        properties = "spring.config.import=optional:classpath:config/application-secret.yml")
@Import({
    SecurityConfig.class,
    JwtAuthenticationFilter.class,
    JwtAuthenticationEntryPoint.class
})
class SharedGroupControllerTest {

    private static final UUID APP_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String ACCESS_TOKEN = "access-token";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private SharedGroupService sharedGroupService;
    @MockitoBean private IdempotencyService idempotencyService;

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
        CreateSharedGroupResponse replayedResponse =
                new CreateSharedGroupResponse(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        "우리 집",
                        "ABC234EF",
                        SharedGroupRole.HOST,
                        null,
                        null);
        when(idempotencyService.execute(
                        eq(APP_USER_ID.toString()),
                        any(UUID.class),
                        eq("POST"),
                        eq("/api/v1/shared-groups"),
                        any(),
                        eq(CreateSharedGroupResponse.class),
                        eq(SharedGroupSuccessCode.SHARED_GROUP_CREATED),
                        any()))
                .thenReturn(new IdempotencyService.IdempotencyExecution<>(replayedResponse, true));

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

    @Test
    void 빈_공유_그룹_이름은_400을_반환한다() throws Exception {
        givenAuthenticatedUser();

        mockMvc.perform(
                        post("/api/v1/shared-groups")
                                .header("Authorization", bearerToken())
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 목록_size가_범위를_벗어나면_400을_반환한다() throws Exception {
        givenAuthenticatedUser();

        mockMvc.perform(
                        get("/api/v1/shared-groups")
                                .header("Authorization", bearerToken())
                                .queryParam("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void HOST의_이름_수정_요청은_성공_응답을_반환한다() throws Exception {
        UUID sharedGroupId = UUID.randomUUID();
        givenAuthenticatedUser();
        when(sharedGroupService.updateName(APP_USER_ID, sharedGroupId, "여름 여행"))
                .thenReturn(
                        new SharedGroupUpdateResponse(
                                sharedGroupId, "여름 여행", Instant.parse("2026-07-03T12:00:00Z")));

        mockMvc.perform(
                        patch("/api/v1/shared-groups/{sharedGroupId}", sharedGroupId)
                                .header("Authorization", bearerToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"여름 여행\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SHARED_GROUP_UPDATED"))
                .andExpect(jsonPath("$.data.id").value(sharedGroupId.toString()))
                .andExpect(jsonPath("$.data.name").value("여름 여행"));

        verify(sharedGroupService).updateName(APP_USER_ID, sharedGroupId, "여름 여행");
    }

    @Test
    void HOST의_삭제_요청은_성공_응답을_반환한다() throws Exception {
        UUID sharedGroupId = UUID.randomUUID();
        givenAuthenticatedUser();

        mockMvc.perform(
                        delete("/api/v1/shared-groups/{sharedGroupId}", sharedGroupId)
                                .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SHARED_GROUP_DELETED"));

        verify(sharedGroupService).delete(APP_USER_ID, sharedGroupId);
    }

    @Test
    void 수정_요청에_인증_정보가_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(
                        patch("/api/v1/shared-groups/{sharedGroupId}", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"새 이름\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void MEMBER의_수정_요청은_403을_반환한다() throws Exception {
        UUID sharedGroupId = UUID.randomUUID();
        givenAuthenticatedUser();
        when(sharedGroupService.updateName(APP_USER_ID, sharedGroupId, "새 이름"))
                .thenThrow(
                        new BusinessException(
                                SharedGroupErrorCode.ONLY_HOST_CAN_UPDATE_SHARED_GROUP));

        mockMvc.perform(
                        patch("/api/v1/shared-groups/{sharedGroupId}", sharedGroupId)
                                .header("Authorization", bearerToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"새 이름\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ONLY_HOST_CAN_UPDATE_SHARED_GROUP"));
    }

    @Test
    void 공유_그룹을_찾을_수_없으면_404를_반환한다() throws Exception {
        UUID sharedGroupId = UUID.randomUUID();
        givenAuthenticatedUser();
        doThrow(new BusinessException(SharedGroupErrorCode.SHARED_GROUP_NOT_FOUND))
                .when(sharedGroupService)
                .delete(APP_USER_ID, sharedGroupId);

        mockMvc.perform(
                        delete("/api/v1/shared-groups/{sharedGroupId}", sharedGroupId)
                                .header("Authorization", bearerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARED_GROUP_NOT_FOUND"));
    }

    private void givenAuthenticatedUser() {
        when(jwtTokenProvider.verifyAccessToken(ACCESS_TOKEN))
                .thenReturn(APP_USER_ID);
    }

    private String bearerToken() {
        return "Bearer " + ACCESS_TOKEN;
    }
}
