package org.zipzip.zipzipserver.domain.auth.controller;

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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/apple")
    public BaseResponse<LoginResponse> loginWithApple(
            @Valid @RequestBody AppleLoginRequest request) {
        return BaseResponse.success(
                AuthSuccessCode.AUTH_LOGIN_SUCCESS, authService.loginWithApple(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<BaseResponse<TokenRefreshResponse>> refreshTokens(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
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
