package org.zipzip.zipzipserver.domain.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zipzip.zipzipserver.domain.auth.code.AuthSuccessCode;
import org.zipzip.zipzipserver.domain.auth.dto.request.AppleLoginRequest;
import org.zipzip.zipzipserver.domain.auth.dto.response.LoginResponse;
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
}
