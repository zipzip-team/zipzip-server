package org.zipzip.zipzipserver.domain.sharedgroup.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zipzip.zipzipserver.domain.sharedgroup.code.SharedGroupSuccessCode;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupMemberListResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.service.SharedGroupMemberService;
import org.zipzip.zipzipserver.global.response.BaseResponse;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shared-groups")
@Tag(name = "공유 그룹 멤버", description = "공유 그룹 멤버 조회 API")
@SecurityRequirement(name = "bearerAuth")
public class SharedGroupMemberController {

    private final SharedGroupMemberService sharedGroupMemberService;

    @GetMapping("/{sharedGroupId}/members")
    @Operation(summary = "공유 그룹 멤버 목록 조회", description = "현재 사용자가 참여한 공유 그룹의 멤버를 커서 기반으로 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "유효하지 않은 커서 또는 size"),
        @ApiResponse(responseCode = "401", description = "인증 필요"),
        @ApiResponse(responseCode = "404", description = "그룹이 없거나 참여하지 않은 그룹")
    })
    public BaseResponse<SharedGroupMemberListResponse> findMembers(
            @PathVariable UUID sharedGroupId,
            @AuthenticationPrincipal UUID appUserId,
            @Parameter(description = "다음 페이지 조회용 커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "조회할 항목 수 (1~100)", example = "50")
                    @RequestParam(defaultValue = "50")
                    @Min(1)
                    @Max(100)
                    int size) {
        return BaseResponse.success(
                SharedGroupSuccessCode.SHARED_GROUP_MEMBER_LIST_FOUND,
                sharedGroupMemberService.findMembers(sharedGroupId, appUserId, cursor, size));
    }
}
