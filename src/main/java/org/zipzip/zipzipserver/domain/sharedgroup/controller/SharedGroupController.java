package org.zipzip.zipzipserver.domain.sharedgroup.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zipzip.zipzipserver.domain.sharedgroup.code.SharedGroupSuccessCode;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.request.CreateSharedGroupRequest;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.request.SharedGroupNameUpdateRequest;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.CreateSharedGroupResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.CreateSharedGroupResponseEnvelope;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupDetailResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupListResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupUpdateResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.service.SharedGroupService;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyResponseSupport;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyResult;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyService;
import org.zipzip.zipzipserver.global.response.BaseResponse;
import org.zipzip.zipzipserver.global.security.AuthenticatedUser;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/shared-groups")
@Tag(name = "공유 그룹", description = "공유 그룹 생성·조회·수정·삭제 API")
@SecurityRequirement(name = "bearerAuth")
public class SharedGroupController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String POST_SHARED_GROUPS_PATH = "/api/v1/shared-groups";

    private final SharedGroupService sharedGroupService;
    private final IdempotencyService idempotencyService;

    @GetMapping
    @Operation(summary = "내 공유 그룹 목록 조회", description = "현재 사용자가 참여한 공유 그룹을 커서 기반으로 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "유효하지 않은 커서 또는 size"),
        @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public BaseResponse<SharedGroupListResponse> findMySharedGroups(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Parameter(description = "다음 페이지 조회용 커서") @RequestParam(required = false) String cursor,
            @Parameter(description = "조회할 항목 수 (1~100)", example = "20")
                    @RequestParam(required = false)
                    @Min(1)
                    @Max(100)
                    Integer size) {
        return BaseResponse.success(
                SharedGroupSuccessCode.SHARED_GROUP_LIST_FOUND,
                sharedGroupService.findMySharedGroups(authenticatedUser.appUserId(), cursor, size));
    }

    @PostMapping
    @Operation(summary = "공유 그룹 생성", description = "공유 그룹을 생성하고 현재 사용자를 HOST 멤버로 추가합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "생성 성공",
                content =
                        @Content(
                                schema =
                                        @Schema(
                                                implementation =
                                                        CreateSharedGroupResponseEnvelope.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "status": 201,
                                                          "code": "SHARED_GROUP_CREATED",
                                                          "message": "공유 그룹을 생성했습니다.",
                                                          "data": {
                                                            "id": "11111111-1111-1111-1111-111111111111",
                                                            "name": "우리 집",
                                                            "inviteCode": "ABC234EF",
                                                            "myRole": "HOST",
                                                            "createdBy": {"id": "22222222-2222-2222-2222-222222222222", "displayName": "집집이"},
                                                            "createdAt": "2026-07-10T00:00:00Z"
                                                          }
                                                        }
                                                        """))),
        @ApiResponse(responseCode = "400", description = "유효하지 않은 요청 또는 멱등성 키"),
        @ApiResponse(responseCode = "401", description = "인증 필요"),
        @ApiResponse(responseCode = "409", description = "멱등성 키 재사용 또는 처리 중인 요청")
    })
    public ResponseEntity<?> createSharedGroup(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Parameter(description = "요청 재시도 식별용 UUID", required = true)
                    @RequestHeader(IDEMPOTENCY_KEY_HEADER)
                    String idempotencyKeyHeader,
            @Valid @RequestBody CreateSharedGroupRequest request) {
        UUID idempotencyKey = IdempotencyResponseSupport.parseKey(idempotencyKeyHeader);
        IdempotencyResult idempotencyResult =
                idempotencyService.execute(
                        authenticatedUser.appUserId().toString(),
                        idempotencyKey,
                        "POST",
                        POST_SHARED_GROUPS_PATH,
                        request,
                        () -> createSharedGroupResponse(authenticatedUser, request));

        return IdempotencyResponseSupport.toResponse(idempotencyResult);
    }

    @GetMapping("/{sharedGroupId}")
    @Operation(summary = "공유 그룹 상세 조회", description = "현재 사용자가 참여한 공유 그룹의 상세 정보를 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "401", description = "인증 필요"),
        @ApiResponse(responseCode = "404", description = "그룹이 없거나 참여하지 않은 그룹")
    })
    public BaseResponse<SharedGroupDetailResponse> findSharedGroup(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID sharedGroupId) {
        return BaseResponse.success(
                SharedGroupSuccessCode.SHARED_GROUP_FOUND,
                sharedGroupService.findSharedGroup(authenticatedUser.appUserId(), sharedGroupId));
    }

    @PatchMapping("/{sharedGroupId}")
    @Operation(summary = "공유 그룹 이름 수정", description = "HOST만 공유 그룹 이름을 수정할 수 있습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공"),
        @ApiResponse(responseCode = "400", description = "유효하지 않은 이름"),
        @ApiResponse(responseCode = "401", description = "인증 필요"),
        @ApiResponse(responseCode = "403", description = "HOST 권한 필요"),
        @ApiResponse(responseCode = "404", description = "그룹이 없거나 참여하지 않은 그룹")
    })
    public BaseResponse<SharedGroupUpdateResponse> updateName(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID sharedGroupId,
            @Valid @RequestBody SharedGroupNameUpdateRequest request) {
        return BaseResponse.success(
                SharedGroupSuccessCode.SHARED_GROUP_UPDATED,
                sharedGroupService.updateName(
                        authenticatedUser.appUserId(), sharedGroupId, request.name()));
    }

    @DeleteMapping("/{sharedGroupId}")
    @Operation(summary = "공유 그룹 삭제", description = "HOST만 공유 그룹을 soft delete할 수 있습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "삭제 성공"),
        @ApiResponse(responseCode = "401", description = "인증 필요"),
        @ApiResponse(responseCode = "403", description = "HOST 권한 필요"),
        @ApiResponse(responseCode = "404", description = "그룹이 없거나 참여하지 않은 그룹")
    })
    public BaseResponse<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID sharedGroupId) {
        sharedGroupService.delete(authenticatedUser.appUserId(), sharedGroupId);
        return BaseResponse.success(SharedGroupSuccessCode.SHARED_GROUP_DELETED);
    }

    private ResponseEntity<?> createSharedGroupResponse(
            AuthenticatedUser authenticatedUser, CreateSharedGroupRequest request) {
        CreateSharedGroupResponse response =
                sharedGroupService.createSharedGroup(authenticatedUser.appUserId(), request);
        return ResponseEntity.status(SharedGroupSuccessCode.SHARED_GROUP_CREATED.getHttpStatus())
                .body(BaseResponse.success(SharedGroupSuccessCode.SHARED_GROUP_CREATED, response));
    }
}
