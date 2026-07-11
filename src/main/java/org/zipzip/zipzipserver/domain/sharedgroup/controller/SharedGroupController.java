package org.zipzip.zipzipserver.domain.sharedgroup.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
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
import org.zipzip.zipzipserver.global.idempotency.IdempotencyService;
import org.zipzip.zipzipserver.global.response.BaseResponse;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/shared-groups")
@Tag(
        name = "공유 그룹",
        description =
                "현재 로그인 사용자의 공유 그룹 생성·조회·이름 수정·삭제 API입니다. 모든 API는 Bearer Access Token이 필요합니다.")
@SecurityRequirement(name = "bearerAuth")
public class SharedGroupController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String POST_SHARED_GROUPS_PATH = "/api/v1/shared-groups";

    private final SharedGroupService sharedGroupService;
    private final IdempotencyService idempotencyService;

    @GetMapping
    @Operation(
            summary = "내 공유 그룹 목록 조회",
            description =
                    "현재 사용자의 활성 멤버십만 참여일시 내림차순으로 조회합니다. 다음 페이지 요청에는 이전"
                            + " 응답의 nextCursor를 수정하지 않고 그대로 전달합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "유효하지 않은 커서 또는 size"),
        @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public BaseResponse<SharedGroupListResponse> findMySharedGroups(
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @Parameter(description = "이전 응답의 nextCursor를 그대로 전달하는 불투명 커서")
                    @RequestParam(required = false)
                    String cursor,
            @Parameter(description = "조회할 항목 수. 1~100, 생략 시 20", example = "20")
                    @RequestParam(required = false)
                    @Min(1)
                    @Max(100)
                    Integer size) {
        return BaseResponse.success(
                SharedGroupSuccessCode.SHARED_GROUP_LIST_FOUND,
                sharedGroupService.findMySharedGroups(appUserId, cursor, size));
    }

    @PostMapping
    @Operation(
            summary = "공유 그룹 생성",
            description =
                    "공유 그룹과 고정 초대 코드, 현재 사용자의 HOST 멤버십을 생성합니다. Idempotency-Key에는"
                            + " 클라이언트가 생성한 UUID를 전달하고, 네트워크 재시도에는 같은 UUID와 동일한 요청 본문을"
                            + " 사용합니다. 재전송된 성공 응답에는 Idempotency-Replayed: true 헤더가 포함됩니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "생성 성공",
                headers =
                        @Header(
                                name = "Idempotency-Replayed",
                                description = "저장된 성공 응답을 재전송한 경우에만 true",
                                schema = @Schema(type = "boolean", allowableValues = "true")),
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
        @ApiResponse(
                responseCode = "400",
                description = "INVALID_REQUEST, INVALID_SHARED_GROUP_NAME"),
        @ApiResponse(responseCode = "401", description = "인증 필요"),
        @ApiResponse(
                responseCode = "409",
                description = "IDEMPOTENCY_KEY_REUSED, IDEMPOTENCY_REQUEST_IN_PROGRESS"),
        @ApiResponse(responseCode = "500", description = "INVITE_CODE_GENERATION_FAILED")
    })
    public ResponseEntity<?> createSharedGroup(
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @Parameter(
                            name = IDEMPOTENCY_KEY_HEADER,
                            in = ParameterIn.HEADER,
                            description = "공유 그룹 생성 재시도 식별자(UUID). 같은 논리적 요청의 재시도에는 같은 값을 사용합니다.",
                            required = true,
                            schema = @Schema(type = "string", format = "uuid"),
                            example = "54cf8d7e-a23e-4e76-90f7-603f122b1507")
                    @RequestHeader(IDEMPOTENCY_KEY_HEADER)
                    String idempotencyKeyHeader,
            @Valid @RequestBody CreateSharedGroupRequest request) {
        UUID idempotencyKey = IdempotencyResponseSupport.parseKey(idempotencyKeyHeader);
        IdempotencyService.IdempotencyExecution<CreateSharedGroupResponse> idempotencyExecution =
                idempotencyService.execute(
                        appUserId.toString(),
                        idempotencyKey,
                        "POST",
                        POST_SHARED_GROUPS_PATH,
                        request,
                        CreateSharedGroupResponse.class,
                        SharedGroupSuccessCode.SHARED_GROUP_CREATED,
                        () -> sharedGroupService.createSharedGroup(appUserId, request));

        ResponseEntity.BodyBuilder response =
                ResponseEntity.status(SharedGroupSuccessCode.SHARED_GROUP_CREATED.getHttpStatus());
        if (idempotencyExecution.replayed()) {
            response.header("Idempotency-Replayed", "true");
        }
        return response.body(
                BaseResponse.success(
                        SharedGroupSuccessCode.SHARED_GROUP_CREATED,
                        idempotencyExecution.response()));
    }

    @GetMapping("/{sharedGroupId}")
    @Operation(
            summary = "공유 그룹 상세 조회",
            description =
                    "현재 사용자의 활성 멤버십이 있는 공유 그룹만 조회합니다. createdBy는 최초 생성자이고,"
                            + " myRole은 요청자의 현재 그룹 역할입니다. 초대 코드는 이 응답에 포함되지 않습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "401", description = "인증 필요"),
        @ApiResponse(responseCode = "404", description = "그룹이 없거나 참여하지 않은 그룹")
    })
    public BaseResponse<SharedGroupDetailResponse> findSharedGroup(
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @Parameter(
                            description = "조회할 공유 그룹 식별자",
                            required = true,
                            example = "b8a5f612-25d7-4ec3-9d1d-59684de40664")
                    @PathVariable
                    UUID sharedGroupId) {
        return BaseResponse.success(
                SharedGroupSuccessCode.SHARED_GROUP_FOUND,
                sharedGroupService.findSharedGroup(appUserId, sharedGroupId));
    }

    @PatchMapping("/{sharedGroupId}")
    @Operation(
            summary = "공유 그룹 이름 수정",
            description =
                    "활성 HOST만 이름을 수정할 수 있습니다. 요청의 name은 앞뒤 공백을 제거한 뒤 1~100자인지"
                            + " 검증하며, 응답에는 정규화된 이름과 수정 시각을 반환합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공"),
        @ApiResponse(
                responseCode = "400",
                description = "INVALID_REQUEST, INVALID_SHARED_GROUP_NAME"),
        @ApiResponse(responseCode = "401", description = "인증 필요"),
        @ApiResponse(responseCode = "403", description = "HOST 권한 필요"),
        @ApiResponse(responseCode = "404", description = "그룹이 없거나 참여하지 않은 그룹")
    })
    public BaseResponse<SharedGroupUpdateResponse> updateName(
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @Parameter(
                            description = "이름을 수정할 공유 그룹 식별자",
                            required = true,
                            example = "b8a5f612-25d7-4ec3-9d1d-59684de40664")
                    @PathVariable
                    UUID sharedGroupId,
            @Valid @RequestBody SharedGroupNameUpdateRequest request) {
        return BaseResponse.success(
                SharedGroupSuccessCode.SHARED_GROUP_UPDATED,
                sharedGroupService.updateName(appUserId, sharedGroupId, request.name()));
    }

    @DeleteMapping("/{sharedGroupId}")
    @Operation(
            summary = "공유 그룹 삭제",
            description =
                    "활성 HOST만 실행할 수 있습니다. 공유 그룹·하위 공유집·사진을 soft delete하여 즉시 접근을"
                            + " 차단하며, 요청 본문은 없습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "삭제 성공"),
        @ApiResponse(responseCode = "401", description = "인증 필요"),
        @ApiResponse(responseCode = "403", description = "HOST 권한 필요"),
        @ApiResponse(responseCode = "404", description = "그룹이 없거나 참여하지 않은 그룹")
    })
    public BaseResponse<Void> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @Parameter(
                            description = "삭제할 공유 그룹 식별자",
                            required = true,
                            example = "b8a5f612-25d7-4ec3-9d1d-59684de40664")
                    @PathVariable
                    UUID sharedGroupId) {
        sharedGroupService.delete(appUserId, sharedGroupId);
        return BaseResponse.success(SharedGroupSuccessCode.SHARED_GROUP_DELETED);
    }
}
