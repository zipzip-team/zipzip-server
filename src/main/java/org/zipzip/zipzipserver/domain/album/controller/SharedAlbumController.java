package org.zipzip.zipzipserver.domain.album.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zipzip.zipzipserver.domain.album.code.SharedAlbumSuccessCode;
import org.zipzip.zipzipserver.domain.album.dto.request.SharedAlbumNameRequest;
import org.zipzip.zipzipserver.domain.album.dto.response.SharedAlbumRenameResponse;
import org.zipzip.zipzipserver.domain.album.dto.response.SharedAlbumResponse;
import org.zipzip.zipzipserver.domain.album.service.SharedAlbumService;
import org.zipzip.zipzipserver.global.response.BaseResponse;

@Tag(name = "공유집(앨범)", description = "공유집(앨범) 상세 조회·이름 수정·삭제 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shared-albums/{sharedAlbumId}")
public class SharedAlbumController {

    private final SharedAlbumService sharedAlbumService;

    @Operation(
            summary = "공유집(앨범) 상세 조회",
            description =
                    "현재 사용자가 활성 멤버인 앨범만 조회합니다. photoCount는 활성 shared_album_photo"
                            + " 매핑과 활성 사진 기준의 실시간 수이며, isCreator는 요청자 본인이 생성자인지 나타냅니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "404", description = "SHARED_ALBUM_NOT_FOUND")
    })
    @GetMapping
    public BaseResponse<SharedAlbumResponse> getAlbum(
            @Parameter(description = "조회할 공유집(앨범) 식별자", required = true, example = "59ce0d18-a53e-4197-9c3c-e82331adc097")
                    @PathVariable
                    UUID sharedAlbumId,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId) {
        return BaseResponse.success(
                SharedAlbumSuccessCode.SHARED_ALBUM_FOUND,
                sharedAlbumService.getAlbum(sharedAlbumId, appUserId));
    }

    @Operation(
            summary = "공유집(앨범) 이름 수정",
            description =
                    "공유 그룹의 활성 HOST와 MEMBER 모두 수정할 수 있으며 생성자 제한은 없습니다. name은 앞뒤"
                            + " 공백을 제거한 뒤 1~100자인지 검증하고, 응답에는 정규화된 이름을 반환합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "INVALID_REQUEST, INVALID_SHARED_ALBUM_NAME"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "404", description = "SHARED_ALBUM_NOT_FOUND")
    })
    @PatchMapping
    public BaseResponse<SharedAlbumRenameResponse> renameAlbum(
            @Parameter(description = "이름을 수정할 공유집(앨범) 식별자", required = true, example = "59ce0d18-a53e-4197-9c3c-e82331adc097")
                    @PathVariable
                    UUID sharedAlbumId,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @RequestBody SharedAlbumNameRequest request) {
        return BaseResponse.success(
                SharedAlbumSuccessCode.SHARED_ALBUM_UPDATED,
                sharedAlbumService.renameAlbum(sharedAlbumId, appUserId, request));
    }

    @Operation(
            summary = "공유집(앨범) 삭제",
            description =
                    "공유집(앨범)을 soft delete하고 그 안의 shared_album_photo 매핑을 모두 즉시 물리 삭제합니다. 마지막 소속"
                            + " 공유집(앨범)을 잃는 사진은 원본도 함께 soft delete됩니다. 생성자 본인이거나, 생성자가 탈퇴한 경우 상위"
                            + " 공유 그룹 방장만 삭제할 수 있습니다. 30일 뒤 shared_album 행을 물리 삭제합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "삭제 성공"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "403", description = "NOT_SHARED_ALBUM_CREATOR"),
        @ApiResponse(responseCode = "404", description = "SHARED_ALBUM_NOT_FOUND")
    })
    @DeleteMapping
    public BaseResponse<Void> deleteAlbum(
            @Parameter(description = "삭제할 공유집(앨범) 식별자", required = true, example = "59ce0d18-a53e-4197-9c3c-e82331adc097")
                    @PathVariable
                    UUID sharedAlbumId,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId) {
        sharedAlbumService.deleteAlbum(sharedAlbumId, appUserId);
        return BaseResponse.success(SharedAlbumSuccessCode.SHARED_ALBUM_DELETED);
    }
}
