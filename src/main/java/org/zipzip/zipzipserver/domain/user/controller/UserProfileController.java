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

@Tag(name = "사용자", description = "내 프로필 조회·수정과 사용자 탈퇴 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me")
public class UserProfileController {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_ACCESS_TOKEN_EXAMPLE = "Bearer eyJhbGciOiJIUzI1NiJ9...";

    private final UserProfileService userProfileService;

    @Operation(
            summary = "내 프로필 조회",
            security = @SecurityRequirement(name = "bearerAuth"),
            parameters =
                    @Parameter(
                            name = AUTHORIZATION_HEADER,
                            in = ParameterIn.HEADER,
                            required = true,
                            description = "Bearer Access Token",
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
            security = @SecurityRequirement(name = "bearerAuth"),
            parameters =
                    @Parameter(
                            name = AUTHORIZATION_HEADER,
                            in = ParameterIn.HEADER,
                            required = true,
                            description = "Bearer Access Token",
                            example = BEARER_ACCESS_TOKEN_EXAMPLE))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "내 프로필 수정 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "INVALID_DISPLAY_NAME"),
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
            security = @SecurityRequirement(name = "bearerAuth"),
            parameters =
                    @Parameter(
                            name = AUTHORIZATION_HEADER,
                            in = ParameterIn.HEADER,
                            required = true,
                            description = "Bearer Access Token",
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
