package org.zipzip.zipzipserver.domain.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zipzip.zipzipserver.domain.user.code.UserSuccessCode;
import org.zipzip.zipzipserver.domain.user.dto.request.UpdateUserProfileRequest;
import org.zipzip.zipzipserver.domain.user.dto.response.UserProfileResponse;
import org.zipzip.zipzipserver.domain.user.service.UserProfileService;
import org.zipzip.zipzipserver.global.response.BaseResponse;

@Tag(name = "사용자", description = "현재 로그인 사용자의 프로필 조회·수정과 계정 탈퇴 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me")
public class UserProfileController {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_ACCESS_TOKEN_EXAMPLE = "Bearer eyJhbGciOiJIUzI1NiJ9...";

    private final UserProfileService userProfileService;

    @Operation(
            summary = "내 프로필 조회",
            description =
                    "Authorization 헤더의 Bearer Access Token으로 현재 사용자를 식별해 본인 프로필만"
                            + " 반환합니다. 사용자 ID를 경로 또는 쿼리로 전달하지 않습니다.",
            security = @SecurityRequirement(name = "bearerAuth"),
            parameters =
                    @Parameter(
                            name = AUTHORIZATION_HEADER,
                            in = ParameterIn.HEADER,
                            required = true,
                            description =
                                    "로그인 또는 토큰 갱신 응답에서 받은 현재 세션의 Bearer Access Token."
                                            + " Bearer 접두사를 포함합니다.",
                            example = BEARER_ACCESS_TOKEN_EXAMPLE))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "내 프로필 조회 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    })
    @GetMapping
    public BaseResponse<UserProfileResponse> getMyProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId) {
        return BaseResponse.success(
                UserSuccessCode.USER_PROFILE_FOUND, userProfileService.getMyProfile(appUserId));
    }

    @Operation(
            summary = "내 프로필 수정",
            description =
                    "요청 본문에는 displayName만 전달합니다. 서버는 앞뒤 공백을 제거한 뒤 1~50자인지"
                            + " 검증하고, 성공 응답에는 정규화된 표시 이름을 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth"),
            parameters =
                    @Parameter(
                            name = AUTHORIZATION_HEADER,
                            in = ParameterIn.HEADER,
                            required = true,
                            description =
                                    "로그인 또는 토큰 갱신 응답에서 받은 현재 세션의 Bearer Access Token."
                                            + " Bearer 접두사를 포함합니다.",
                            example = BEARER_ACCESS_TOKEN_EXAMPLE))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "내 프로필 수정 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "INVALID_REQUEST, INVALID_DISPLAY_NAME"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    })
    @PatchMapping
    public BaseResponse<UserProfileResponse> updateMyProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @RequestBody UpdateUserProfileRequest request) {
        return BaseResponse.success(
                UserSuccessCode.USER_PROFILE_UPDATED,
                userProfileService.updateMyProfile(appUserId, request));
    }

    @Operation(
            summary = "사용자 탈퇴",
            description =
                    "현재 사용자 계정을 soft delete하고 표시 이름을 탈퇴한 사용자로 바꿉니다. 모든 활성"
                            + " Refresh Token을 폐기하고 사진 좋아요·MEMBER 멤버십을 삭제하며, 사용자가 만든"
                            + " 활성 공유 그룹은 soft delete합니다. 요청 본문은 없습니다.",
            security = @SecurityRequirement(name = "bearerAuth"),
            parameters =
                    @Parameter(
                            name = AUTHORIZATION_HEADER,
                            in = ParameterIn.HEADER,
                            required = true,
                            description =
                                    "탈퇴할 현재 계정의 Bearer Access Token. Bearer 접두사를 포함합니다.",
                            example = BEARER_ACCESS_TOKEN_EXAMPLE))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "사용자 탈퇴 성공"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND")
    })
    @DeleteMapping
    public BaseResponse<Void> withdraw(
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId) {
        userProfileService.withdraw(appUserId);
        return BaseResponse.success(UserSuccessCode.USER_WITHDRAWN);
    }
}
