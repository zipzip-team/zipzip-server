package org.zipzip.zipzipserver.domain.album.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
import org.zipzip.zipzipserver.domain.album.code.SharedAlbumSuccessCode;
import org.zipzip.zipzipserver.domain.album.dto.request.SharedAlbumNameRequest;
import org.zipzip.zipzipserver.domain.album.dto.response.SharedAlbumListResponse;
import org.zipzip.zipzipserver.domain.album.dto.response.SharedAlbumResponse;
import org.zipzip.zipzipserver.domain.album.service.SharedAlbumService;
import org.zipzip.zipzipserver.global.response.BaseResponse;

@Tag(name = "공유집(앨범)", description = "공유집(앨범) 조회·관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shared-groups/{sharedGroupId}/shared-albums")
public class SharedGroupAlbumController {

    private final SharedAlbumService sharedAlbumService;

    @Operation(
            summary = "공유집(앨범) 목록 조회",
            description = "생성일시 내림차순으로 조회하며 동일 시각은 공유집(앨범) ID로 순서를 고정합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "INVALID_CURSOR"),
        @ApiResponse(responseCode = "404", description = "SHARED_GROUP_NOT_FOUND")
    })
    @GetMapping
    public BaseResponse<SharedAlbumListResponse> listAlbums(
            @Parameter(description = "공유 그룹 식별자") @PathVariable UUID sharedGroupId,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @Parameter(description = "이전 응답의 불투명 cursor") @RequestParam(required = false)
                    String cursor,
            @Parameter(description = "페이지 크기(1~100)", example = "20")
                    @RequestParam(required = false)
                    Integer size) {
        return BaseResponse.success(
                SharedAlbumSuccessCode.SHARED_ALBUM_LIST_FOUND,
                sharedAlbumService.listAlbums(sharedGroupId, appUserId, cursor, size));
    }

    @Operation(summary = "공유집(앨범) 생성", description = "사진이 없는 빈 공유집(앨범)도 생성할 수 있습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "생성 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "INVALID_SHARED_ALBUM_NAME"),
        @ApiResponse(responseCode = "404", description = "SHARED_GROUP_NOT_FOUND")
    })
    @PostMapping
    public ResponseEntity<BaseResponse<SharedAlbumResponse>> createAlbum(
            @Parameter(description = "공유집(앨범)을 생성할 공유 그룹 식별자") @PathVariable UUID sharedGroupId,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @Parameter(
                            description = "생성 재시도 식별자. 같은 요청 재시도에는 같은 UUID를 사용합니다.",
                            required = true,
                            example = "54cf8d7e-a23e-4e76-90f7-603f122b1507")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @RequestBody SharedAlbumNameRequest request) {
        SharedAlbumResponse response =
                sharedAlbumService.createAlbum(sharedGroupId, appUserId, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(SharedAlbumSuccessCode.SHARED_ALBUM_CREATED, response));
    }
}
