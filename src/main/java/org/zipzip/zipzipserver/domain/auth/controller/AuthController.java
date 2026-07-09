package org.zipzip.zipzipserver.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zipzip.zipzipserver.domain.auth.code.AuthSuccessCode;
import org.zipzip.zipzipserver.domain.auth.dto.request.AppleLoginRequest;
import org.zipzip.zipzipserver.domain.auth.dto.request.TokenRefreshRequest;
import org.zipzip.zipzipserver.domain.auth.dto.response.LoginResponse;
import org.zipzip.zipzipserver.domain.auth.dto.response.TokenRefreshResponse;
import org.zipzip.zipzipserver.domain.auth.service.AuthService;
import org.zipzip.zipzipserver.global.response.BaseResponse;

@Tag(name = "인증", description = "Apple 로그인과 토큰 갱신 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Apple 로그인",
            description =
                    "Apple identity token과 authorization code를 검증한 뒤 사용자를 생성·복구하거나 기존"
                            + " 사용자로 로그인하고 Access Token과 Refresh Token을 발급합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "로그인 성공", useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "DISPLAY_NAME_REQUIRED, INVALID_DISPLAY_NAME"),
        @ApiResponse(
                responseCode = "401",
                description = "INVALID_APPLE_TOKEN, INVALID_APPLE_AUTHORIZATION_CODE")
    })
    @PostMapping("/apple")
    public BaseResponse<LoginResponse> loginWithApple(
            @Valid @RequestBody AppleLoginRequest request) {
        return BaseResponse.success(
                AuthSuccessCode.AUTH_LOGIN_SUCCESS, authService.loginWithApple(request));
    }

    @Operation(
            summary = "토큰 갱신",
            description =
                    "Refresh Token을 검증한 뒤 기존 Refresh Token을 폐기하고 같은 token family의 새"
                            + " Access Token과 Refresh Token을 발급합니다. 같은 Idempotency-Key와 같은 요청이"
                            + " 재시도되면 최초 성공 응답을 재전송합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "토큰 갱신 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "INVALID_REQUEST"),
        @ApiResponse(
                responseCode = "401",
                description =
                        "INVALID_REFRESH_TOKEN, REFRESH_TOKEN_EXPIRED,"
                                + " REFRESH_TOKEN_REUSE_DETECTED"),
        @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND"),
        @ApiResponse(
                responseCode = "409",
                description = "IDEMPOTENCY_KEY_REUSED, IDEMPOTENCY_REQUEST_IN_PROGRESS")
    })
    @PostMapping("/refresh")
    public ResponseEntity<BaseResponse<TokenRefreshResponse>> refreshTokens(
            @Parameter(
                            description = "토큰 갱신 재시도 식별자. 같은 요청 재시도에는 같은 UUID를 사용합니다.",
                            required = true,
                            example = "54cf8d7e-a23e-4e76-90f7-603f122b1507")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @Valid @RequestBody TokenRefreshRequest request) {
        AuthService.TokenRefreshResult result = authService.refreshTokens(request, idempotencyKey);
        HttpHeaders headers = new HttpHeaders();
        if (result.replayed()) {
            headers.add("Idempotency-Replayed", "true");
        }

        return ResponseEntity.status(AuthSuccessCode.AUTH_TOKEN_REFRESHED.getHttpStatus())
                .headers(headers)
                .body(
                        BaseResponse.success(
                                AuthSuccessCode.AUTH_TOKEN_REFRESHED, result.response()));
    }
}
