package org.zipzip.zipzipserver.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.zipzip.zipzipserver.domain.auth.dto.request.LogoutRequest;
import org.zipzip.zipzipserver.domain.auth.jwt.JwtTokenProvider;
import org.zipzip.zipzipserver.domain.auth.service.AuthService;
import org.zipzip.zipzipserver.global.code.GlobalErrorCode;
import org.zipzip.zipzipserver.global.exception.BusinessException;
import org.zipzip.zipzipserver.global.security.JwtAuthenticationEntryPoint;
import org.zipzip.zipzipserver.global.security.JwtAuthenticationFilter;
import org.zipzip.zipzipserver.global.security.SecurityConfig;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
@TestPropertySource(
        properties = "spring.config.import=optional:classpath:config/application-secret.yml")
class AuthControllerSecurityTest {

    private static final UUID APP_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String ACCESS_TOKEN = "valid-access-token";
    private static final String REFRESH_TOKEN = "refresh-token";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AuthService authService;

    @MockitoBean private JwtTokenProvider jwtTokenProvider;

    @Test
    void Authorization_헤더가_없으면_로그아웃을_거부한다() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"" + REFRESH_TOKEN + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(authService, never()).logout(any(), any());
    }

    @Test
    void 잘못된_Bearer_토큰이면_로그아웃을_거부한다() throws Exception {
        given(jwtTokenProvider.verifyAccessToken("invalid-access-token"))
                .willThrow(new BusinessException(GlobalErrorCode.UNAUTHORIZED));

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .header("Authorization", "Bearer invalid-access-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"" + REFRESH_TOKEN + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(authService, never()).logout(any(), any());
    }

    @Test
    void Refresh_Token이_공백이면_INVALID_REQUEST를_반환한다() throws Exception {
        given(jwtTokenProvider.verifyAccessToken(ACCESS_TOKEN)).willReturn(APP_USER_ID);

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .header("Authorization", "Bearer " + ACCESS_TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(authService, never()).logout(any(), any());
    }

    @Test
    void 정상_로그아웃은_성공_응답을_반환한다() throws Exception {
        given(jwtTokenProvider.verifyAccessToken(ACCESS_TOKEN)).willReturn(APP_USER_ID);

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .header("Authorization", "Bearer " + ACCESS_TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"" + REFRESH_TOKEN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("AUTH_LOGOUT_SUCCESS"))
                .andExpect(jsonPath("$.message").value("로그아웃했습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(authService).logout(eq(APP_USER_ID), any(LogoutRequest.class));
    }

    @Test
    void Apple_로그인은_인증_없이_호출할_수_있다() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/apple")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "identityToken": "identity-token",
                                          "authorizationCode": "authorization-code",
                                          "nonce": "nonce",
                                          "displayName": "집집이"
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_SUCCESS"));
    }
}
