package org.zipzip.zipzipserver.domain.chat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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
import org.zipzip.zipzipserver.domain.chat.code.ChatSuccessCode;
import org.zipzip.zipzipserver.domain.chat.dto.request.CreateChatMessageRequest;
import org.zipzip.zipzipserver.domain.chat.dto.response.ChatMessageResponse;
import org.zipzip.zipzipserver.domain.chat.dto.response.ChatTimelineResponse;
import org.zipzip.zipzipserver.domain.chat.service.ChatService;
import org.zipzip.zipzipserver.global.response.BaseResponse;

@Tag(name = "채팅", description = "공유 그룹 채팅 타임라인 조회와 일반 메시지 작성 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shared-groups/{sharedGroupId}/chat-messages")
public class ChatController {

    private final ChatService chatService;

    @Operation(
            summary = "그룹 채팅 타임라인 조회",
            description =
                    "공유 그룹을 채팅방으로 사용합니다. 일반 메시지와 해당 공유 그룹의 활성 사진 댓글을" + " 최신순 타임라인으로 함께 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "타임라인 조회 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "INVALID_REQUEST, INVALID_CURSOR"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "404", description = "SHARED_GROUP_NOT_FOUND")
    })
    @GetMapping
    public BaseResponse<ChatTimelineResponse> getTimeline(
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @Parameter(
                            description = "채팅방으로 사용할 공유 그룹 식별자",
                            required = true,
                            example = "b8a5f612-25d7-4ec3-9d1d-59684de40664")
                    @PathVariable
                    UUID sharedGroupId,
            @Parameter(
                            description = "이전 응답의 nextCursor를 그대로 전달하는 불투명 cursor",
                            example =
                                    "MjAyNi0wNy0wM1QxMDoxNTozMFp8Q0hBVF9NRVNTQUdFfDIyYjUwYzY5LWNlMjktNDcxNS05ODI1LWRkMjdmNDg2OGJlYQ")
                    @RequestParam(required = false)
                    String cursor,
            @Parameter(description = "페이지 크기. 1~100, 기본값 30", example = "30")
                    @RequestParam(required = false)
                    Integer size) {
        return BaseResponse.success(
                ChatSuccessCode.SHARED_GROUP_CHAT_TIMELINE_FOUND,
                chatService.getTimeline(appUserId, sharedGroupId, cursor, size));
    }

    @Operation(
            summary = "그룹 채팅 메시지 작성",
            description = "공유 그룹의 채팅방에 일반 메시지를 작성합니다. 같은 Idempotency-Key와 같은 요청은 최초 성공 응답을 재전송합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "메시지 작성 성공",
                headers =
                        @Header(
                                name = "Idempotency-Replayed",
                                description = "저장된 성공 응답을 재전송한 경우에만 true",
                                schema = @Schema(type = "boolean", allowableValues = "true")),
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "INVALID_REQUEST, INVALID_CHAT_MESSAGE_CONTENT"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "404", description = "SHARED_GROUP_NOT_FOUND"),
        @ApiResponse(
                responseCode = "409",
                description = "IDEMPOTENCY_KEY_REUSED, IDEMPOTENCY_REQUEST_IN_PROGRESS")
    })
    @PostMapping
    public ResponseEntity<BaseResponse<ChatMessageResponse>> createMessage(
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @Parameter(
                            description = "채팅방으로 사용할 공유 그룹 식별자",
                            required = true,
                            example = "b8a5f612-25d7-4ec3-9d1d-59684de40664")
                    @PathVariable
                    UUID sharedGroupId,
            @Parameter(
                            name = "Idempotency-Key",
                            in = ParameterIn.HEADER,
                            description = "메시지 작성 재시도 식별자(UUID). 같은 요청 재시도에는 같은 값을 사용합니다.",
                            required = true,
                            schema = @Schema(type = "string", format = "uuid"),
                            example = "54cf8d7e-a23e-4e76-90f7-603f122b1507")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @RequestBody CreateChatMessageRequest request) {
        ChatService.CreateChatMessageResult result =
                chatService.createMessage(appUserId, sharedGroupId, idempotencyKey, request);
        HttpHeaders headers = new HttpHeaders();
        if (result.replayed()) {
            headers.add("Idempotency-Replayed", "true");
        }

        return ResponseEntity.status(
                        ChatSuccessCode.SHARED_GROUP_CHAT_MESSAGE_CREATED.getHttpStatus())
                .headers(headers)
                .body(
                        BaseResponse.success(
                                ChatSuccessCode.SHARED_GROUP_CHAT_MESSAGE_CREATED,
                                result.response()));
    }
}
