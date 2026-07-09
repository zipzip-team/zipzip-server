package org.zipzip.zipzipserver.domain.sharedgroup.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zipzip.zipzipserver.domain.sharedgroup.code.SharedGroupSuccessCode;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.request.SharedGroupJoinRequest;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.InviteCodeResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.service.SharedGroupInviteService;
import org.zipzip.zipzipserver.global.code.GlobalErrorCode;
import org.zipzip.zipzipserver.global.exception.BusinessException;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyResult;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyService;
import org.zipzip.zipzipserver.global.response.BaseResponse;
import org.zipzip.zipzipserver.global.security.AuthenticatedUser;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shared-groups")
public class SharedGroupInviteController {

    private static final String IDEMPOTENCY_REPLAYED_HEADER = "Idempotency-Replayed";
    private static final String POST_SHARED_GROUP_JOIN_PATH = "/api/v1/shared-groups/join";

    private final SharedGroupInviteService sharedGroupInviteService;
    private final IdempotencyService idempotencyService;

    @GetMapping("/{sharedGroupId}/invite-code")
    public BaseResponse<InviteCodeResponse> findInviteCode(
            @PathVariable UUID sharedGroupId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return BaseResponse.success(
                SharedGroupSuccessCode.INVITE_CODE_FOUND,
                sharedGroupInviteService.findInviteCode(sharedGroupId, authenticatedUser.appUserId()));
    }

    @PostMapping("/join")
    public ResponseEntity<?> join(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SharedGroupJoinRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        UUID parsedIdempotencyKey = parseIdempotencyKey(idempotencyKey);
        IdempotencyResult idempotencyResult =
                idempotencyService.execute(
                        authenticatedUser.appUserId().toString(),
                        parsedIdempotencyKey,
                        "POST",
                        POST_SHARED_GROUP_JOIN_PATH,
                        request,
                        () ->
                                ResponseEntity.status(
                                                SharedGroupSuccessCode.SHARED_GROUP_JOINED
                                                        .getHttpStatus())
                                        .body(
                                                BaseResponse.success(
                                                        SharedGroupSuccessCode.SHARED_GROUP_JOINED,
                                                        sharedGroupInviteService.join(
                                                                authenticatedUser.appUserId(), request))));

        ResponseEntity.BodyBuilder response = ResponseEntity.status(idempotencyResult.statusCode());
        if (idempotencyResult.replayed()) {
            response.header(IDEMPOTENCY_REPLAYED_HEADER, "true");
        }
        return response.body(idempotencyResult.body());
    }

    @DeleteMapping("/{sharedGroupId}/members/me")
    public BaseResponse<Void> leave(
            @PathVariable UUID sharedGroupId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        sharedGroupInviteService.leave(sharedGroupId, authenticatedUser.appUserId());
        return BaseResponse.success(SharedGroupSuccessCode.SHARED_GROUP_LEFT);
    }

    private UUID parseIdempotencyKey(String idempotencyKey) {
        try {
            return UUID.fromString(idempotencyKey);
        } catch (Exception exception) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }
    }
}
