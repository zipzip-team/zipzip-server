package org.zipzip.zipzipserver.domain.sharedgroup.controller;

import static org.zipzip.zipzipserver.global.auth.AuthenticatedUserFilter.CURRENT_APP_USER_ID_ATTRIBUTE;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zipzip.zipzipserver.domain.sharedgroup.code.SharedGroupSuccessCode;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.request.SharedGroupJoinRequest;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.InviteCodeResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.service.SharedGroupInviteService;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyService;
import org.zipzip.zipzipserver.global.response.BaseResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shared-groups")
public class SharedGroupInviteController {

    private final SharedGroupInviteService sharedGroupInviteService;
    private final IdempotencyService idempotencyService;

    @GetMapping("/{sharedGroupId}/invite-code")
    public BaseResponse<InviteCodeResponse> findInviteCode(
            @PathVariable UUID sharedGroupId,
            @RequestAttribute(CURRENT_APP_USER_ID_ATTRIBUTE) UUID appUserId) {
        return BaseResponse.success(
                SharedGroupSuccessCode.INVITE_CODE_FOUND,
                sharedGroupInviteService.findInviteCode(sharedGroupId, appUserId));
    }

    @PostMapping("/join")
    public ResponseEntity<?> join(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SharedGroupJoinRequest request,
            @RequestAttribute(CURRENT_APP_USER_ID_ATTRIBUTE) UUID appUserId,
            HttpServletRequest servletRequest) {
        return idempotencyService.execute(
                appUserId,
                HttpMethod.POST.name(),
                servletRequest.getRequestURI(),
                idempotencyKey,
                request,
                () ->
                        ResponseEntity.status(
                                        SharedGroupSuccessCode.SHARED_GROUP_JOINED.getHttpStatus())
                                .body(
                                        BaseResponse.success(
                                                SharedGroupSuccessCode.SHARED_GROUP_JOINED,
                                                sharedGroupInviteService.join(
                                                        appUserId, request))));
    }

    @DeleteMapping("/{sharedGroupId}/members/me")
    public BaseResponse<Void> leave(
            @PathVariable UUID sharedGroupId,
            @RequestAttribute(CURRENT_APP_USER_ID_ATTRIBUTE) UUID appUserId) {
        sharedGroupInviteService.leave(sharedGroupId, appUserId);
        return BaseResponse.success(SharedGroupSuccessCode.SHARED_GROUP_LEFT);
    }
}
