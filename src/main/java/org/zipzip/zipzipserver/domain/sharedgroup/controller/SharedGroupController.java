package org.zipzip.zipzipserver.domain.sharedgroup.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zipzip.zipzipserver.domain.sharedgroup.code.SharedGroupSuccessCode;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.request.CreateSharedGroupRequest;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.CreateSharedGroupResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupDetailResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupListResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.service.SharedGroupService;
import org.zipzip.zipzipserver.global.code.GlobalErrorCode;
import org.zipzip.zipzipserver.global.exception.BusinessException;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyResult;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyService;
import org.zipzip.zipzipserver.global.response.BaseResponse;
import org.zipzip.zipzipserver.global.security.AuthenticatedUser;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shared-groups")
public class SharedGroupController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String IDEMPOTENCY_REPLAYED_HEADER = "Idempotency-Replayed";
    private static final String POST_SHARED_GROUPS_PATH = "/api/v1/shared-groups";

    private final SharedGroupService sharedGroupService;
    private final IdempotencyService idempotencyService;

    @GetMapping
    public BaseResponse<SharedGroupListResponse> findMySharedGroups(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return BaseResponse.success(
                SharedGroupSuccessCode.SHARED_GROUP_LIST_FOUND,
                sharedGroupService.findMySharedGroups(authenticatedUser.appUserId(), cursor, size));
    }

    @PostMapping
    public ResponseEntity<?> createSharedGroup(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKeyHeader,
            @RequestBody CreateSharedGroupRequest request) {
        UUID idempotencyKey = parseIdempotencyKey(idempotencyKeyHeader);
        IdempotencyResult idempotencyResult =
                idempotencyService.execute(
                        authenticatedUser.appUserId().toString(),
                        idempotencyKey,
                        "POST",
                        POST_SHARED_GROUPS_PATH,
                        request,
                        () -> createSharedGroupResponse(authenticatedUser, request));

        ResponseEntity.BodyBuilder response = ResponseEntity.status(idempotencyResult.statusCode());
        if (idempotencyResult.replayed()) {
            response.header(IDEMPOTENCY_REPLAYED_HEADER, "true");
        }
        return response.body(idempotencyResult.body());
    }

    @GetMapping("/{sharedGroupId}")
    public BaseResponse<SharedGroupDetailResponse> findSharedGroup(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID sharedGroupId) {
        return BaseResponse.success(
                SharedGroupSuccessCode.SHARED_GROUP_FOUND,
                sharedGroupService.findSharedGroup(authenticatedUser.appUserId(), sharedGroupId));
    }

    private ResponseEntity<?> createSharedGroupResponse(
            AuthenticatedUser authenticatedUser, CreateSharedGroupRequest request) {
        CreateSharedGroupResponse response =
                sharedGroupService.createSharedGroup(authenticatedUser.appUserId(), request);
        return ResponseEntity.status(SharedGroupSuccessCode.SHARED_GROUP_CREATED.getHttpStatus())
                .body(BaseResponse.success(SharedGroupSuccessCode.SHARED_GROUP_CREATED, response));
    }

    private UUID parseIdempotencyKey(String idempotencyKeyHeader) {
        try {
            return UUID.fromString(idempotencyKeyHeader);
        } catch (Exception exception) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }
    }
}
