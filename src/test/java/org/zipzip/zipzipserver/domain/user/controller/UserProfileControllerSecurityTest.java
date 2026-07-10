package org.zipzip.zipzipserver.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.zipzip.zipzipserver.domain.auth.jwt.JwtTokenProvider;
import org.zipzip.zipzipserver.domain.user.dto.request.UpdateUserProfileRequest;
import org.zipzip.zipzipserver.domain.user.dto.response.UserProfileResponse;
import org.zipzip.zipzipserver.domain.user.service.UserProfileService;
import org.zipzip.zipzipserver.global.security.JwtAuthenticationEntryPoint;
import org.zipzip.zipzipserver.global.security.JwtAuthenticationFilter;
import org.zipzip.zipzipserver.global.security.SecurityConfig;

@WebMvcTest(UserProfileController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
@TestPropertySource(
        properties = "spring.config.import=optional:classpath:config/application-secret.yml")
class UserProfileControllerSecurityTest {

    private static final UUID APP_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ACCESS_TOKEN = "valid-access-token";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private UserProfileService userProfileService;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;

    @Test
    void Authorization_헤더가_없으면_내_프로필_조회를_거부한다() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(userProfileService, never()).getMyProfile(any());
    }

    @Test
    void 인증된_사용자는_사용자_이름만_포함한_내_프로필을_조회한다() throws Exception {
        given(jwtTokenProvider.verifyAccessToken(ACCESS_TOKEN)).willReturn(APP_USER_ID);
        given(userProfileService.getMyProfile(APP_USER_ID))
                .willReturn(new UserProfileResponse("집집이"));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER_PROFILE_FOUND"))
                .andExpect(jsonPath("$.data.displayName").value("집집이"))
                .andExpect(jsonPath("$.data.id").doesNotExist());

        verify(userProfileService).getMyProfile(APP_USER_ID);
    }

    @Test
    void 인증된_사용자는_내_프로필을_수정한다() throws Exception {
        given(jwtTokenProvider.verifyAccessToken(ACCESS_TOKEN)).willReturn(APP_USER_ID);
        given(
                        userProfileService.updateMyProfile(
                                eq(APP_USER_ID), any(UpdateUserProfileRequest.class)))
                .willReturn(new UserProfileResponse("새 집집이"));

        mockMvc.perform(
                        patch("/api/v1/users/me")
                                .header("Authorization", "Bearer " + ACCESS_TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"displayName\":\"새 집집이\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER_PROFILE_UPDATED"))
                .andExpect(jsonPath("$.data.displayName").value("새 집집이"));
    }

    @Test
    void 인증된_사용자는_탈퇴할_수_있다() throws Exception {
        given(jwtTokenProvider.verifyAccessToken(ACCESS_TOKEN)).willReturn(APP_USER_ID);

        mockMvc.perform(
                        delete("/api/v1/users/me")
                                .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER_WITHDRAWN"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(userProfileService).withdraw(APP_USER_ID);
    }
}
