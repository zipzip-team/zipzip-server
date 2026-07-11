package org.zipzip.zipzipserver.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zipzip.zipzipserver.domain.auth.code.AuthSuccessCode;
import org.zipzip.zipzipserver.domain.auth.dto.request.AppleLoginRequest;
import org.zipzip.zipzipserver.domain.auth.dto.request.LogoutRequest;
import org.zipzip.zipzipserver.domain.auth.dto.request.TokenRefreshRequest;
import org.zipzip.zipzipserver.domain.auth.dto.response.LoginResponse;
import org.zipzip.zipzipserver.domain.auth.dto.response.TokenRefreshResponse;
import org.zipzip.zipzipserver.domain.auth.service.AuthService;
import org.zipzip.zipzipserver.global.response.BaseResponse;

@Tag(name = "인증", description = "Apple 로그인으로 인증 세션을 만들고, Refresh Token 회전 및 현재 기기 로그아웃을 처리합니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Apple 로그인",
            description =
                    "iOS의 ASAuthorizationAppleIDCredential에서 받은 identityToken과 authorizationCode를"
                            + " 요청 본문에 전달합니다. nonce는 Apple 로그인 요청 시 생성한 원문이며,"
                            + " identityToken의 nonce claim과 정확히 일치해야 합니다. 사용자 신규 가입 또는"
                            + " 탈퇴 계정 복구에는 displayName이 필요합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "로그인 성공", useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "INVALID_REQUEST, DISPLAY_NAME_REQUIRED, INVALID_DISPLAY_NAME"),
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
            summary = "로그아웃",
            description =
                    "Authorization 헤더의 Access Token으로 현재 사용자를 식별하고, 요청 본문의"
                            + " Refresh Token 하나만 폐기합니다. 다른 기기의 Refresh Token은 유지되며,"
                            + " 이미 폐기된 현재 사용자 소유 토큰을 다시 보내도 성공합니다.",
            security = @SecurityRequirement(name = "bearerAuth"),
            parameters =
                    @Parameter(
                            name = HttpHeaders.AUTHORIZATION,
                            in = ParameterIn.HEADER,
                            required = true,
                            description = "현재 세션의 Bearer Access Token",
                            example = "Bearer eyJhbGciOiJIUzI1NiJ9..."))
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

    @Operation(
            summary = "토큰 갱신",
            description =
                    "요청 본문의 Refresh Token을 한 번만 회전합니다. Idempotency-Key에는 클라이언트가"
                            + " 생성한 UUID를 넣고, 네트워크 재시도에는 반드시 같은 UUID와 같은 Refresh Token을"
                            + " 사용합니다. 같은 요청의 재시도 성공 응답에는 Idempotency-Replayed: true 헤더가"
                            + " 포함됩니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "토큰 갱신 성공",
                headers =
                        @Header(
                                name = "Idempotency-Replayed",
                                description = "같은 Idempotency-Key 요청의 저장된 성공 응답을 재전송했을 때만 true",
                                schema = @Schema(type = "boolean", allowableValues = "true")),
                useReturnTypeSchema = true),
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
                            name = "Idempotency-Key",
                            in = ParameterIn.HEADER,
                            description = "토큰 갱신 재시도 식별자. 같은 요청 재시도에는 같은 UUID를 사용합니다.",
                            required = true,
                            schema = @Schema(type = "string", format = "uuid"),
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
