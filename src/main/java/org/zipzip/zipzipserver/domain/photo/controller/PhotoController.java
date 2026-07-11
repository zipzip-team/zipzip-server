package org.zipzip.zipzipserver.domain.photo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zipzip.zipzipserver.domain.photo.code.PhotoSuccessCode;
import org.zipzip.zipzipserver.domain.photo.dto.request.PhotoMetadataUpdateRequest;
import org.zipzip.zipzipserver.domain.photo.dto.response.PhotoMetadataUpdateResponse;
import org.zipzip.zipzipserver.domain.photo.service.PhotoService;
import org.zipzip.zipzipserver.global.response.BaseResponse;

@Tag(name = "사진", description = "사진 메타데이터 수정과 원본 사진 삭제 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/photos")
public class PhotoController {

    private final PhotoService photoService;

    @Operation(
            summary = "사진 메타데이터 수정",
            description =
                    "필드를 생략하면 기존 값을 유지하고, null을 명시하면 해당 값을 제거합니다. 위치 필드(latitude, longitude,"
                            + " locationName)는 셋을 함께 보내 갱신하거나 셋 다 null로 보내 제거합니다. 업로더 본인만 수정할 수"
                            + " 있습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "수정 성공", useReturnTypeSchema = true),
        @ApiResponse(
                responseCode = "400",
                description = "INVALID_TAKEN_AT, INVALID_PHOTO_LOCATION"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "403", description = "NOT_PHOTO_UPLOADER"),
        @ApiResponse(responseCode = "404", description = "PHOTO_NOT_FOUND")
    })
    @PatchMapping("/{photoId}")
    public BaseResponse<PhotoMetadataUpdateResponse> updateMetadata(
            @Parameter(description = "메타데이터를 수정할 사진 식별자", required = true, example = "385ff765-b20c-49a2-8e62-e1457784aa15")
                    @PathVariable
                    UUID photoId,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @Schema(implementation = PhotoMetadataUpdateRequest.class) @RequestBody
                    Map<String, Object> requestBody) {
        return BaseResponse.success(
                PhotoSuccessCode.PHOTO_UPDATED,
                photoService.updateMetadata(photoId, appUserId, requestBody));
    }

    @Operation(
            summary = "사진 삭제",
            description =
                    "사진을 soft delete해 즉시 접근을 차단합니다. 원본 삭제는 복구할 수 없고, 소속된 모든 공유집(앨범)에서 함께"
                            + " 차단됩니다. 업로더 본인이거나, 업로더가 탈퇴한 경우 상위 공유 그룹 방장만 삭제할 수 있습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "삭제 성공"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "403", description = "NOT_PHOTO_UPLOADER"),
        @ApiResponse(responseCode = "404", description = "PHOTO_NOT_FOUND")
    })
    @DeleteMapping("/{photoId}")
    public BaseResponse<Void> deletePhoto(
            @Parameter(description = "원본을 삭제할 사진 식별자", required = true, example = "385ff765-b20c-49a2-8e62-e1457784aa15")
                    @PathVariable
                    UUID photoId,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId) {
        photoService.deletePhoto(photoId, appUserId);
        return BaseResponse.success(PhotoSuccessCode.PHOTO_DELETED);
    }
}
