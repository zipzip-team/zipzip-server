package org.zipzip.zipzipserver.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zipzip.zipzipserver.domain.auth.code.AuthSuccessCode;
import org.zipzip.zipzipserver.domain.auth.dto.request.DevelopmentTokenIssueRequest;
import org.zipzip.zipzipserver.domain.auth.dto.response.LoginResponse;
import org.zipzip.zipzipserver.domain.auth.service.AuthService;
import org.zipzip.zipzipserver.global.response.BaseResponse;

@Profile("dev")
@Tag(name = "개발 인증", description = "dev 프로필에서만 노출되는 클라이언트 개발 테스트용 인증 API입니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/dev/auth")
public class DevelopmentAuthController {

    private final AuthService authService;

    @Operation(
            summary = "개발용 토큰 발급",
            description =
                    "Apple 로그인 없이 개발용 사용자의 Access Token과 Refresh Token을 발급합니다."
                            + " dev 프로필에서만 등록되며 운영 환경에는 노출되지 않습니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "개발용 토큰 발급 성공",
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "INVALID_REQUEST, DISPLAY_NAME_REQUIRED, INVALID_DISPLAY_NAME")
    })
    @PostMapping("/tokens")
    public BaseResponse<LoginResponse> issueTokens(
            @Valid @RequestBody DevelopmentTokenIssueRequest request) {
        return BaseResponse.success(
                AuthSuccessCode.AUTH_DEVELOPMENT_TOKEN_ISSUED,
                authService.issueDevelopmentTokens(request));
    }

    @Operation(
            summary = "개발용 사용자 삭제",
            description =
                    "개발용 토큰 발급 API가 만든 사용자를 탈퇴 처리합니다. 활성 Refresh Token을 폐기하고"
                            + " 사용자 관련 탈퇴 정리를 수행합니다. dev 프로필에서만 등록됩니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "개발용 사용자 삭제 성공"),
        @ApiResponse(responseCode = "400", description = "INVALID_REQUEST"),
        @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    })
    @DeleteMapping("/users/{testUserKey}")
    public BaseResponse<Void> deleteUser(
            @Parameter(
                            name = "testUserKey",
                            in = ParameterIn.PATH,
                            required = true,
                            description = "삭제할 개발용 사용자 식별 키",
                            example = "ios-tester-1")
                    @PathVariable
                    String testUserKey) {
        authService.deleteDevelopmentUser(testUserKey);
        return BaseResponse.success(AuthSuccessCode.AUTH_DEVELOPMENT_USER_DELETED);
    }
}
