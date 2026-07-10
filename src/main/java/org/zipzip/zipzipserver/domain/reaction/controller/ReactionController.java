package org.zipzip.zipzipserver.domain.reaction.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zipzip.zipzipserver.domain.reaction.code.ReactionSuccessCode;
import org.zipzip.zipzipserver.domain.reaction.dto.request.PhotoCommentCreateRequest;
import org.zipzip.zipzipserver.domain.reaction.dto.response.PhotoCommentListResponse;
import org.zipzip.zipzipserver.domain.reaction.dto.response.PhotoCommentResponse;
import org.zipzip.zipzipserver.domain.reaction.dto.response.PhotoDetailResponse;
import org.zipzip.zipzipserver.domain.reaction.dto.response.PhotoLikeResponse;
import org.zipzip.zipzipserver.domain.reaction.service.ReactionService;
import org.zipzip.zipzipserver.global.response.BaseResponse;

@Tag(name = "사진 반응·댓글", description = "사진 상세 조회, 좋아요, 사진 댓글 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/photos")
public class ReactionController {

    private final ReactionService reactionService;

    @Operation(
            summary = "사진 상세 조회",
            description =
                    "공유집(앨범)에서 선택한 단일 사진의 메타데이터와 반응 요약을 조회합니다. 원본·썸네일 URL은 호출마다 새로"
                            + " 발급하는 presigned GET URL입니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "PHOTO_FOUND", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "404", description = "PHOTO_NOT_FOUND")
    })
    @GetMapping("/{photoId}")
    public BaseResponse<PhotoDetailResponse> getPhotoDetail(
            @Parameter(description = "사진 식별자") @PathVariable UUID photoId,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId) {
        return BaseResponse.success(
                ReactionSuccessCode.PHOTO_FOUND,
                reactionService.getPhotoDetail(photoId, appUserId));
    }

    @Operation(
            summary = "사진 좋아요 설정",
            description = "좋아요가 이미 있으면 새 행을 만들지 않고 현재 상태를 반환하는 멱등 API입니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "PHOTO_LIKED", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "404", description = "PHOTO_NOT_FOUND")
    })
    @PutMapping("/{photoId}/like")
    public BaseResponse<PhotoLikeResponse> likePhoto(
            @Parameter(description = "사진 식별자") @PathVariable UUID photoId,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId) {
        return BaseResponse.success(
                ReactionSuccessCode.PHOTO_LIKED, reactionService.likePhoto(photoId, appUserId));
    }

    @Operation(
            summary = "사진 좋아요 취소",
            description = "좋아요가 없어도 현재 상태를 반환하는 멱등 API입니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "PHOTO_UNLIKED",
                useReturnTypeSchema = true),
        @ApiResponse(responseCode = "404", description = "PHOTO_NOT_FOUND")
    })
    @DeleteMapping("/{photoId}/like")
    public BaseResponse<PhotoLikeResponse> unlikePhoto(
            @Parameter(description = "사진 식별자") @PathVariable UUID photoId,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId) {
        return BaseResponse.success(
                ReactionSuccessCode.PHOTO_UNLIKED, reactionService.unlikePhoto(photoId, appUserId));
    }

    @Operation(
            summary = "사진 댓글 목록 조회",
            description = "댓글을 작성일시 오름차순과 댓글 ID 동점 처리 기준으로 커서 페이지네이션합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "PHOTO_COMMENT_LIST_FOUND",
                useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "INVALID_CURSOR"),
        @ApiResponse(responseCode = "404", description = "PHOTO_NOT_FOUND")
    })
    @GetMapping("/{photoId}/comments")
    public BaseResponse<PhotoCommentListResponse> listComments(
            @Parameter(description = "사진 식별자") @PathVariable UUID photoId,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @Parameter(description = "이전 응답의 불투명 cursor") @RequestParam(required = false)
                    String cursor,
            @Parameter(description = "페이지 크기(1~100)", example = "20")
                    @RequestParam(required = false)
                    Integer size) {
        return BaseResponse.success(
                ReactionSuccessCode.PHOTO_COMMENT_LIST_FOUND,
                reactionService.listComments(photoId, appUserId, cursor, size));
    }

    @Operation(
            summary = "사진 댓글 작성",
            description = "댓글을 작성합니다. 같은 Idempotency-Key와 동일한 댓글 작성 요청을 재시도하면 최초 성공 응답을 재전송합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "PHOTO_COMMENT_CREATED",
                useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "INVALID_PHOTO_COMMENT_CONTENT, INVALID_REQUEST"),
        @ApiResponse(responseCode = "404", description = "PHOTO_NOT_FOUND"),
        @ApiResponse(
                responseCode = "409",
                description = "IDEMPOTENCY_KEY_REUSED, IDEMPOTENCY_REQUEST_IN_PROGRESS")
    })
    @PostMapping("/{photoId}/comments")
    public ResponseEntity<BaseResponse<PhotoCommentResponse>> createComment(
            @Parameter(description = "사진 식별자") @PathVariable UUID photoId,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @Parameter(
                            description = "댓글 작성 재시도 식별자. 같은 요청 재시도에는 같은 UUID를 사용합니다.",
                            required = true,
                            example = "54cf8d7e-a23e-4e76-90f7-603f122b1507")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @RequestBody PhotoCommentCreateRequest request) {
        ReactionService.PhotoCommentCreateResult result =
                reactionService.createComment(photoId, appUserId, idempotencyKey, request);
        HttpHeaders headers = new HttpHeaders();
        if (result.replayed()) {
            headers.add("Idempotency-Replayed", "true");
        }
        return ResponseEntity.status(ReactionSuccessCode.PHOTO_COMMENT_CREATED.getHttpStatus())
                .headers(headers)
                .body(
                        BaseResponse.success(
                                ReactionSuccessCode.PHOTO_COMMENT_CREATED, result.response()));
    }
}
