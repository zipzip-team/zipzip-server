package org.zipzip.zipzipserver.domain.sharedgroup.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.zipzip.zipzipserver.domain.auth.jwt.JwtTokenProvider;
import org.zipzip.zipzipserver.domain.sharedgroup.code.SharedGroupErrorCode;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupMemberListResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;
import org.zipzip.zipzipserver.domain.sharedgroup.service.SharedGroupMemberService;
import org.zipzip.zipzipserver.global.code.GlobalErrorCode;
import org.zipzip.zipzipserver.global.exception.BusinessException;
import org.zipzip.zipzipserver.global.exception.GlobalExceptionHandler;
import org.zipzip.zipzipserver.global.security.JwtAuthenticationFilter;
import org.zipzip.zipzipserver.global.security.SecurityConfig;
import org.zipzip.zipzipserver.global.security.JwtAuthenticationEntryPoint;

@WebMvcTest(
        controllers = SharedGroupMemberController.class,
        properties = "spring.config.import=optional:classpath:config/application-secret.yml")
@Import({
    SecurityConfig.class,
    JwtAuthenticationFilter.class,
    JwtAuthenticationEntryPoint.class,
    GlobalExceptionHandler.class
})
class SharedGroupMemberControllerTest {

    private static final UUID SHARED_GROUP_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000000");
    private static final UUID CURRENT_USER_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000000");

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private SharedGroupMemberService sharedGroupMemberService;

    @Test
    void Authorization_헤더가_없으면_401을_응답한다() throws Exception {
        mockMvc.perform(get("/api/v1/shared-groups/{sharedGroupId}/members", SHARED_GROUP_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 유효하지_않은_Access_Token이면_UNAUTHORIZED를_응답한다() throws Exception {
        when(jwtTokenProvider.verifyAccessToken("expired-token"))
                .thenThrow(new BusinessException(GlobalErrorCode.UNAUTHORIZED));

        mockMvc.perform(
                get("/api/v1/shared-groups/{sharedGroupId}/members", SHARED_GROUP_ID)
                                .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 공유_그룹_멤버_목록을_조회한다() throws Exception {
        when(jwtTokenProvider.verifyAccessToken("valid-token"))
                .thenReturn(CURRENT_USER_ID);
        when(sharedGroupMemberService.findMembers(SHARED_GROUP_ID, CURRENT_USER_ID, null, 50))
                .thenReturn(
                        new SharedGroupMemberListResponse(
                                List.of(
                                        new SharedGroupMemberListResponse.Member(
                                                CURRENT_USER_ID,
                                                "집집이",
                                                SharedGroupRole.HOST,
                                                true,
                                                Instant.parse("2026-07-03T10:15:30Z"))),
                                null,
                                false));

        mockMvc.perform(
                        get("/api/v1/shared-groups/{sharedGroupId}/members", SHARED_GROUP_ID)
                                .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SHARED_GROUP_MEMBER_LIST_FOUND"))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].userId").value(CURRENT_USER_ID.toString()))
                .andExpect(jsonPath("$.data.items[0].isMe").value(true))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 잘못된_커서는_400을_응답한다() throws Exception {
        when(jwtTokenProvider.verifyAccessToken("valid-token"))
                .thenReturn(CURRENT_USER_ID);
        when(sharedGroupMemberService.findMembers(
                        eq(SHARED_GROUP_ID), eq(CURRENT_USER_ID), eq("bad"), any(Integer.class)))
                .thenThrow(new BusinessException(GlobalErrorCode.INVALID_CURSOR));

        mockMvc.perform(
                        get("/api/v1/shared-groups/{sharedGroupId}/members", SHARED_GROUP_ID)
                                .queryParam("cursor", "bad")
                                .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    @Test
    void 활성_멤버십이_없으면_404를_응답한다() throws Exception {
        when(jwtTokenProvider.verifyAccessToken("valid-token"))
                .thenReturn(CURRENT_USER_ID);
        when(sharedGroupMemberService.findMembers(SHARED_GROUP_ID, CURRENT_USER_ID, null, 50))
                .thenThrow(new BusinessException(SharedGroupErrorCode.SHARED_GROUP_NOT_FOUND));

        mockMvc.perform(
                        get("/api/v1/shared-groups/{sharedGroupId}/members", SHARED_GROUP_ID)
                                .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARED_GROUP_NOT_FOUND"));
    }
}
