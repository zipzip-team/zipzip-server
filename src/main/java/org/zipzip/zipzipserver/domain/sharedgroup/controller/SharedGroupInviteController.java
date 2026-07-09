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
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupJoinResponseEnvelope;
import org.zipzip.zipzipserver.domain.sharedgroup.service.SharedGroupInviteService;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyResponseSupport;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyResult;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyService;
import org.zipzip.zipzipserver.global.response.BaseResponse;
import org.zipzip.zipzipserver.global.security.AuthenticatedUser;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shared-groups")
@Tag(name = "공유 그룹 초대", description = "초대 코드 조회·참여·탈퇴 API")
@SecurityRequirement(name = "bearerAuth")
public class SharedGroupInviteController {

    private static final String POST_SHARED_GROUP_JOIN_PATH = "/api/v1/shared-groups/join";

    private final SharedGroupInviteService sharedGroupInviteService;
    private final IdempotencyService idempotencyService;

    @GetMapping("/{sharedGroupId}/invite-code")
    @Operation(summary = "초대 코드 조회", description = "현재 사용자가 참여한 공유 그룹의 초대 코드를 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "401", description = "인증 필요"),
        @ApiResponse(responseCode = "404", description = "그룹이 없거나 참여하지 않은 그룹")
    })
    public BaseResponse<InviteCodeResponse> findInviteCode(
            @PathVariable UUID sharedGroupId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return BaseResponse.success(
                SharedGroupSuccessCode.INVITE_CODE_FOUND,
                sharedGroupInviteService.findInviteCode(
                        sharedGroupId, authenticatedUser.appUserId()));
    }

    @PostMapping("/join")
    @Operation(summary = "공유 그룹 참여", description = "초대 코드로 공유 그룹에 참여합니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "참여 성공",
                content =
                        @Content(
                                schema =
                                        @Schema(
                                                implementation =
                                                        SharedGroupJoinResponseEnvelope.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "status": 201,
                                                          "code": "SHARED_GROUP_JOINED",
                                                          "message": "공유 그룹에 참여했습니다.",
                                                          "data": {
                                                            "sharedGroupId": "11111111-1111-1111-1111-111111111111",
                                                            "name": "우리 집",
                                                            "myRole": "MEMBER",
                                                            "joinedAt": "2026-07-10T00:00:00Z"
                                                          }
                                                        }
                                                        """))),
        @ApiResponse(responseCode = "400", description = "유효하지 않은 요청 또는 멱등성 키"),
        @ApiResponse(responseCode = "401", description = "인증 필요"),
        @ApiResponse(responseCode = "409", description = "이미 참여했거나 멱등성 요청 충돌")
    })
    public ResponseEntity<?> join(
            @Parameter(description = "요청 재시도 식별용 UUID", required = true)
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @Valid @RequestBody SharedGroupJoinRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        UUID parsedIdempotencyKey = IdempotencyResponseSupport.parseKey(idempotencyKey);
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
                                                                authenticatedUser.appUserId(),
                                                                request))));

        return IdempotencyResponseSupport.toResponse(idempotencyResult);
    }

    @DeleteMapping("/{sharedGroupId}/members/me")
    @Operation(summary = "공유 그룹 탈퇴", description = "현재 사용자가 공유 그룹에서 탈퇴합니다. HOST는 탈퇴할 수 없습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "탈퇴 성공"),
        @ApiResponse(responseCode = "401", description = "인증 필요"),
        @ApiResponse(responseCode = "403", description = "HOST는 탈퇴 불가"),
        @ApiResponse(responseCode = "404", description = "그룹이 없거나 참여하지 않은 그룹")
    })
    public BaseResponse<Void> leave(
            @PathVariable UUID sharedGroupId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        sharedGroupInviteService.leave(sharedGroupId, authenticatedUser.appUserId());
        return BaseResponse.success(SharedGroupSuccessCode.SHARED_GROUP_LEFT);
    }
}
