package org.zipzip.zipzipserver.domain.album.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zipzip.zipzipserver.domain.album.code.SharedAlbumSuccessCode;
import org.zipzip.zipzipserver.domain.album.dto.request.SharedAlbumIdsRequest;
import org.zipzip.zipzipserver.domain.album.dto.response.SharedAlbumBulkDeleteResponse;
import org.zipzip.zipzipserver.domain.album.service.SharedAlbumService;
import org.zipzip.zipzipserver.global.response.BaseResponse;

@Tag(name = "공유집(앨범)", description = "공유집(앨범) 다중 선택 일괄 삭제 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shared-albums")
public class SharedAlbumBulkController {

    private final SharedAlbumService sharedAlbumService;

    @Operation(
            summary = "공유집(앨범) 일괄 삭제",
            description =
                    "선택한 공유집(앨범)을 모두 soft delete하고 각 앨범의 shared_album_photo 매핑을 즉시 물리"
                            + " 삭제합니다. 마지막 소속 공유집(앨범)을 잃는 사진은 원본도 함께 soft delete됩니다. 상위 공유"
                            + " 그룹의 활성 멤버라면 생성자·방장 여부와 무관하게 누구나 삭제할 수 있습니다. 대상 중 하나라도"
                            + " 존재하지 않거나 활성 멤버십이 없으면 어떤 앨범도 삭제하지 않고 전체 요청이 실패합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "삭제 성공", useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "INVALID_REQUEST, INVALID_SHARED_ALBUM_IDS"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "404", description = "SHARED_ALBUM_NOT_FOUND"),
        @ApiResponse(
                responseCode = "409",
                description = "IDEMPOTENCY_KEY_REUSED, IDEMPOTENCY_REQUEST_IN_PROGRESS")
    })
    @PostMapping("/bulk-delete")
    public BaseResponse<SharedAlbumBulkDeleteResponse> bulkDeleteAlbums(
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @Parameter(
                            name = "Idempotency-Key",
                            in = ParameterIn.HEADER,
                            description = "일괄 삭제 재시도 식별자(UUID). 같은 논리적 요청의 재시도에는 같은 값을 사용합니다.",
                            required = true,
                            schema = @Schema(type = "string", format = "uuid"),
                            example = "7a6e9c2e-3b7a-4c1a-9c3e-8b8f3a2b5f11")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @RequestBody SharedAlbumIdsRequest request) {
        SharedAlbumBulkDeleteResponse response =
                sharedAlbumService.bulkDeleteAlbums(appUserId, idempotencyKey, request);
        return BaseResponse.success(SharedAlbumSuccessCode.SHARED_ALBUMS_DELETED, response);
    }
}
