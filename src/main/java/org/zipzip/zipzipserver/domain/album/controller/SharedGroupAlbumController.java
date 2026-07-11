package org.zipzip.zipzipserver.domain.album.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

@Tag(name = "공유집(앨범)", description = "공유 그룹 안의 공유집(앨범) 목록 조회와 생성 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shared-groups/{sharedGroupId}/shared-albums")
public class SharedGroupAlbumController {

    private final SharedAlbumService sharedAlbumService;

    @Operation(
            summary = "공유집(앨범) 목록 조회",
            description =
                    "현재 사용자가 활성 멤버인 공유 그룹의 앨범을 생성 시각 내림차순으로 조회합니다. 다음"
                            + " 페이지에는 이전 응답의 nextCursor를 수정하지 않고 그대로 전달합니다. size를 생략하면"
                            + " 20이며, 범위를 벗어난 값은 1~100 범위로 보정됩니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "INVALID_CURSOR"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "404", description = "SHARED_GROUP_NOT_FOUND")
    })
    @GetMapping
    public BaseResponse<SharedAlbumListResponse> listAlbums(
            @Parameter(description = "앨범을 조회할 공유 그룹 식별자", required = true, example = "b8a5f612-25d7-4ec3-9d1d-59684de40664")
                    @PathVariable
                    UUID sharedGroupId,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @Parameter(description = "이전 응답의 nextCursor를 그대로 전달하는 불투명 커서") @RequestParam(required = false)
                    String cursor,
            @Parameter(description = "페이지 크기. 생략 시 20이며, 1 미만은 1, 100 초과는 100으로 보정", example = "20")
                    @RequestParam(required = false)
                    Integer size) {
        return BaseResponse.success(
                SharedAlbumSuccessCode.SHARED_ALBUM_LIST_FOUND,
                sharedAlbumService.listAlbums(sharedGroupId, appUserId, cursor, size));
    }

    @Operation(
            summary = "공유집(앨범) 생성",
            description =
                    "현재 사용자가 활성 멤버인 공유 그룹에 사진이 없는 빈 앨범을 생성합니다. Idempotency-Key에는"
                            + " 클라이언트가 생성한 UUID를 전달하고, 네트워크 재시도에는 같은 UUID와 같은 요청 본문을"
                            + " 사용합니다. 같은 요청을 재시도하면 최초 성공 응답을 다시 반환합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "생성 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "INVALID_REQUEST, INVALID_SHARED_ALBUM_NAME"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "404", description = "SHARED_GROUP_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "IDEMPOTENCY_KEY_REUSED, IDEMPOTENCY_REQUEST_IN_PROGRESS")
    })
    @PostMapping
    public ResponseEntity<BaseResponse<SharedAlbumResponse>> createAlbum(
            @Parameter(description = "앨범을 생성할 공유 그룹 식별자", required = true, example = "b8a5f612-25d7-4ec3-9d1d-59684de40664")
                    @PathVariable
                    UUID sharedGroupId,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @Parameter(
                            name = "Idempotency-Key",
                            in = ParameterIn.HEADER,
                            description = "앨범 생성 재시도 식별자(UUID). 같은 논리적 요청의 재시도에는 같은 값을 사용합니다.",
                            required = true,
                            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "uuid"),
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
