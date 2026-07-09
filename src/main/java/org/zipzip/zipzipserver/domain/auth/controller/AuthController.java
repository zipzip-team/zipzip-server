package org.zipzip.zipzipserver.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zipzip.zipzipserver.domain.auth.code.AuthSuccessCode;
import org.zipzip.zipzipserver.domain.auth.dto.request.AppleLoginRequest;
import org.zipzip.zipzipserver.domain.auth.dto.request.LogoutRequest;
import org.zipzip.zipzipserver.domain.auth.dto.response.LoginResponse;
import org.zipzip.zipzipserver.domain.auth.service.AuthService;
import org.zipzip.zipzipserver.global.response.BaseResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
@Tag(name = "인증", description = "Apple 로그인과 인증 세션 관리 API")
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Apple 로그인",
            description = "Apple identity token과 authorization code를 검증해 로그인합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "로그인 성공"),
        @ApiResponse(responseCode = "400", description = "표시 이름 누락 또는 형식 오류"),
        @ApiResponse(responseCode = "401", description = "Apple 토큰 또는 authorization code 검증 실패")
    })
    @PostMapping("/apple")
    public BaseResponse<LoginResponse> loginWithApple(
            @Valid @RequestBody AppleLoginRequest request) {
        return BaseResponse.success(
                AuthSuccessCode.AUTH_LOGIN_SUCCESS, authService.loginWithApple(request));
    }

    @Operation(
            summary = "로그아웃",
            description = "현재 사용자 소유 Refresh Token을 폐기합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "로그아웃 성공"),
        @ApiResponse(responseCode = "400", description = "요청 본문 검증 실패"),
        @ApiResponse(
                responseCode = "401",
                description = "Access Token 검증 실패 또는 유효하지 않은 Refresh Token")
    })
    @PostMapping("/logout")
    public BaseResponse<Void> logout(
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @Valid @RequestBody LogoutRequest request) {
        authService.logout(appUserId, request);
        return BaseResponse.success(AuthSuccessCode.AUTH_LOGOUT_SUCCESS);
    }
}
