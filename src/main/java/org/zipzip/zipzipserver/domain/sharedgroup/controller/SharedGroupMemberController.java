package org.zipzip.zipzipserver.domain.sharedgroup.controller;

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
import org.zipzip.zipzipserver.global.security.AuthenticatedUser;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shared-groups")
public class SharedGroupMemberController {

    private final SharedGroupMemberService sharedGroupMemberService;

    @GetMapping("/{sharedGroupId}/members")
    public BaseResponse<SharedGroupMemberListResponse> findMembers(
            @PathVariable UUID sharedGroupId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return BaseResponse.success(
                SharedGroupSuccessCode.SHARED_GROUP_MEMBER_LIST_FOUND,
                sharedGroupMemberService.findMembers(
                        sharedGroupId, authenticatedUser.appUserId(), cursor, size));
    }
}
