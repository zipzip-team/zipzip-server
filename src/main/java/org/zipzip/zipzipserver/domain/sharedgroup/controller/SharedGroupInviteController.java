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
import org.zipzip.zipzipserver.global.idempotency.IdempotencyService;
import org.zipzip.zipzipserver.global.response.BaseResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shared-groups")
@Tag(name = "공유 그룹 초대", description = "초대 코드 조회, 초대 코드 참여 및 현재 멤버십 탈퇴 API")
@SecurityRequirement(name = "bearerAuth")
public class SharedGroupInviteController {

    private static final String POST_SHARED_GROUP_JOIN_PATH = "/api/v1/shared-groups/join";

    private final SharedGroupInviteService sharedGroupInviteService;
    private final IdempotencyService idempotencyService;

    @GetMapping("/{sharedGroupId}/invite-code")
    @Operation(summary = "초대 코드 조회", description = "현재 사용자가 활성 멤버인 공유 그룹의 고정 초대 코드를 조회합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "401", description = "인증 필요"),
        @ApiResponse(responseCode = "404", description = "그룹이 없거나 참여하지 않은 그룹")
    })
    public BaseResponse<InviteCodeResponse> findInviteCode(
            @Parameter(
                            description = "초대 코드를 조회할 공유 그룹 식별자",
                            required = true,
                            example = "b8a5f612-25d7-4ec3-9d1d-59684de40664")
                    @PathVariable
                    UUID sharedGroupId,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId) {
        return BaseResponse.success(
                SharedGroupSuccessCode.INVITE_CODE_FOUND,
                sharedGroupInviteService.findInviteCode(sharedGroupId, appUserId));
    }

    @PostMapping("/join")
    @Operation(
            summary = "공유 그룹 참여",
            description =
                    "요청 본문의 초대 코드로 활성 공유 그룹에 MEMBER로 참여합니다. Idempotency-Key에는 클라이언트가"
                            + " 생성한 UUID를 사용하고, 같은 참여 요청 재시도에는 같은 UUID와 본문을 전달합니다. 저장된"
                            + " 성공 응답을 재전송하면 Idempotency-Replayed: true 헤더가 포함됩니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "참여 성공",
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
        @ApiResponse(responseCode = "400", description = "INVALID_REQUEST, INVALID_INVITE_CODE"),
        @ApiResponse(responseCode = "401", description = "인증 필요"),
        @ApiResponse(
                responseCode = "409",
                description =
                        "ALREADY_JOINED_SHARED_GROUP, IDEMPOTENCY_KEY_REUSED,"
                                + " IDEMPOTENCY_REQUEST_IN_PROGRESS")
    })
    public ResponseEntity<?> join(
            @Parameter(
                            name = "Idempotency-Key",
                            in = ParameterIn.HEADER,
                            description = "공유 그룹 참여 재시도 식별자(UUID). 같은 논리적 요청의 재시도에는 같은 값을 사용합니다.",
                            required = true,
                            schema = @Schema(type = "string", format = "uuid"),
                            example = "54cf8d7e-a23e-4e76-90f7-603f122b1507")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @Valid @RequestBody SharedGroupJoinRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId) {
        UUID parsedIdempotencyKey = IdempotencyResponseSupport.parseKey(idempotencyKey);
        IdempotencyService.IdempotencyExecution<
                        org.zipzip.zipzipserver.domain.sharedgroup.dto.response
                                .SharedGroupJoinResponse>
                idempotencyExecution =
                        idempotencyService.execute(
                                appUserId.toString(),
                                parsedIdempotencyKey,
                                "POST",
                                POST_SHARED_GROUP_JOIN_PATH,
                                request,
                                org.zipzip.zipzipserver.domain.sharedgroup.dto.response
                                        .SharedGroupJoinResponse.class,
                                SharedGroupSuccessCode.SHARED_GROUP_JOINED,
                                () -> sharedGroupInviteService.join(appUserId, request));

        ResponseEntity.BodyBuilder response =
                ResponseEntity.status(SharedGroupSuccessCode.SHARED_GROUP_JOINED.getHttpStatus());
        if (idempotencyExecution.replayed()) {
            response.header("Idempotency-Replayed", "true");
        }
        return response.body(
                BaseResponse.success(
                        SharedGroupSuccessCode.SHARED_GROUP_JOINED,
                        idempotencyExecution.response()));
    }

    @DeleteMapping("/{sharedGroupId}/members/me")
    @Operation(
            summary = "공유 그룹 탈퇴",
            description =
                    "현재 사용자의 활성 MEMBER 멤버십만 삭제합니다. HOST는 탈퇴할 수 없으며 공유 그룹 삭제를 사용해야 합니다. 요청 본문은"
                            + " 없습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "탈퇴 성공"),
        @ApiResponse(responseCode = "401", description = "인증 필요"),
        @ApiResponse(responseCode = "403", description = "HOST는 탈퇴 불가"),
        @ApiResponse(responseCode = "404", description = "그룹이 없거나 참여하지 않은 그룹")
    })
    public BaseResponse<Void> leave(
            @Parameter(
                            description = "나갈 공유 그룹 식별자",
                            required = true,
                            example = "b8a5f612-25d7-4ec3-9d1d-59684de40664")
                    @PathVariable
                    UUID sharedGroupId,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId) {
        sharedGroupInviteService.leave(sharedGroupId, appUserId);
        return BaseResponse.success(SharedGroupSuccessCode.SHARED_GROUP_LEFT);
    }
}
