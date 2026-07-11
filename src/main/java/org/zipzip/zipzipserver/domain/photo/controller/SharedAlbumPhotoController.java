package org.zipzip.zipzipserver.domain.photo.controller;

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
import org.zipzip.zipzipserver.domain.photo.code.PhotoSuccessCode;
import org.zipzip.zipzipserver.domain.photo.dto.request.PhotoIdsRequest;
import org.zipzip.zipzipserver.domain.photo.dto.request.PhotoUploadCompleteRequest;
import org.zipzip.zipzipserver.domain.photo.dto.request.PhotoUploadUrlRequest;
import org.zipzip.zipzipserver.domain.photo.dto.response.PhotoAttachResponse;
import org.zipzip.zipzipserver.domain.photo.dto.response.PhotoBulkDeleteResponse;
import org.zipzip.zipzipserver.domain.photo.dto.response.PhotoDetachResponse;
import org.zipzip.zipzipserver.domain.photo.dto.response.PhotoListResponse;
import org.zipzip.zipzipserver.domain.photo.dto.response.PhotoUploadCompleteResponse;
import org.zipzip.zipzipserver.domain.photo.dto.response.PhotoUploadUrlResponse;
import org.zipzip.zipzipserver.domain.photo.service.PhotoService;
import org.zipzip.zipzipserver.domain.photo.service.PhotoUploadService;
import org.zipzip.zipzipserver.global.response.BaseResponse;

@Tag(name = "사진", description = "공유집(앨범)의 사진 조회·업로드·삭제·첨부·분리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shared-albums/{sharedAlbumId}/photos")
public class SharedAlbumPhotoController {

    private final PhotoUploadService photoUploadService;
    private final PhotoService photoService;

    @Operation(
            summary = "공유집(앨범) 사진 목록 조회",
            description =
                    "`displayAt`(`takenAt` 없으면 `createdAt`) 내림차순 + 사진 ID 동점 처리로 커서 페이지네이션합니다. 각 항목의"
                        + " `originalUrl`/`thumbnailUrl`은 호출마다 새로 발급하는 presigned GET URL이라 영구 저장하지"
                        + " 않습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "INVALID_CURSOR"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "404", description = "SHARED_ALBUM_NOT_FOUND")
    })
    @GetMapping
    public BaseResponse<PhotoListResponse> listPhotos(
            @Parameter(description = "사진을 조회할 공유집(앨범) 식별자", required = true, example = "59ce0d18-a53e-4197-9c3c-e82331adc097")
                    @PathVariable
                    UUID sharedAlbumId,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @Parameter(description = "이전 응답의 nextCursor를 그대로 전달하는 불투명 커서") @RequestParam(required = false)
                    String cursor,
            @Parameter(description = "페이지 크기. 1~100, 생략 시 20", example = "20")
                    @RequestParam(required = false)
                    Integer size) {
        return BaseResponse.success(
                PhotoSuccessCode.PHOTO_LIST_FOUND,
                photoService.listPhotos(sharedAlbumId, appUserId, cursor, size));
    }

    @Operation(
            summary = "사진 업로드 URL 발급",
            description =
                    "최대 20개까지 원본 업로드용 presigned PUT URL을 발급합니다. 아직 `photo` 행을 만들지 않으며, 실제 업로드"
                            + " 후 완료 등록(`/complete`) 호출까지 마쳐야 사진이 생성됩니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "발급 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "INVALID_UPLOAD_METADATA, TOO_MANY_FILES"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "404", description = "SHARED_ALBUM_NOT_FOUND"),
        @ApiResponse(responseCode = "413", description = "FILE_TOO_LARGE"),
        @ApiResponse(responseCode = "415", description = "UNSUPPORTED_IMAGE_TYPE")
    })
    @PostMapping("/upload-urls")
    public BaseResponse<PhotoUploadUrlResponse> issueUploadUrls(
            @Parameter(description = "업로드 URL을 발급할 공유집(앨범) 식별자", required = true, example = "59ce0d18-a53e-4197-9c3c-e82331adc097")
                    @PathVariable
                    UUID sharedAlbumId,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @RequestBody PhotoUploadUrlRequest request) {
        return BaseResponse.success(
                PhotoSuccessCode.PHOTO_UPLOAD_URLS_ISSUED,
                photoUploadService.issueUploadUrls(sharedAlbumId, appUserId, request));
    }

    @Operation(
            summary = "사진 업로드 완료 등록",
            description =
                    "발급받은 objectKey로 원본 업로드를 마친 파일들을 한 번에 등록합니다. 예약(objectKey 발급 대상)이 유효하고"
                            + " Object Storage에 실제 업로드가 끝난 경우에만 등록되며, 하나라도 실패하면 전체 요청이 롤백됩니다.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "등록 성공",
                headers =
                        @Header(
                                name = "Idempotency-Replayed",
                                description = "저장된 성공 응답을 재전송한 경우에만 true",
                                schema = @Schema(type = "boolean", allowableValues = "true")),
                useReturnTypeSchema = true),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "400", description = "INVALID_UPLOAD_METADATA"),
        @ApiResponse(
                responseCode = "404",
                description = "SHARED_ALBUM_NOT_FOUND, UPLOAD_OBJECT_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "UPLOAD_NOT_COMPLETED")
    })
    @PostMapping("/complete")
    public ResponseEntity<BaseResponse<PhotoUploadCompleteResponse>> completeUpload(
            @Parameter(description = "업로드 완료를 등록할 공유집(앨범) 식별자", required = true, example = "59ce0d18-a53e-4197-9c3c-e82331adc097")
                    @PathVariable
                    UUID sharedAlbumId,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @Parameter(
                            name = "Idempotency-Key",
                            in = ParameterIn.HEADER,
                            description = "업로드 완료 등록 재시도 식별자(UUID). 같은 요청 재시도에는 같은 값을 사용합니다.",
                            required = true,
                            schema = @Schema(type = "string", format = "uuid"),
                            example = "54cf8d7e-a23e-4e76-90f7-603f122b1507")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @RequestBody PhotoUploadCompleteRequest request) {
        PhotoUploadService.PhotoUploadCompleteResult result =
                photoUploadService.completeUpload(
                        sharedAlbumId, appUserId, idempotencyKey, request);

        HttpHeaders headers = new HttpHeaders();
        if (result.replayed()) {
            headers.add("Idempotency-Replayed", "true");
        }

        return ResponseEntity.status(PhotoSuccessCode.PHOTOS_CREATED.getHttpStatus())
                .headers(headers)
                .body(BaseResponse.success(PhotoSuccessCode.PHOTOS_CREATED, result.response()));
    }

    @Operation(
            summary = "공유집(앨범) 사진 일괄 삭제",
            description =
                    "요청 사용자가 업로드했거나, 업로더가 탈퇴한 사용자인 경우 요청 사용자가 상위 공유 그룹 방장인 사진만 최대 100개"
                            + " soft delete합니다. 원본 삭제이므로 복구할 수 없고, 다른 공유집(앨범)에서도 함께 접근이 차단됩니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "삭제 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "INVALID_PHOTO_IDS"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "403", description = "NOT_PHOTO_UPLOADER"),
        @ApiResponse(responseCode = "404", description = "SHARED_ALBUM_NOT_FOUND, PHOTO_NOT_FOUND")
    })
    @PostMapping("/bulk-delete")
    public BaseResponse<PhotoBulkDeleteResponse> bulkDelete(
            @Parameter(description = "일괄 삭제할 사진이 속한 공유집(앨범) 식별자", required = true, example = "59ce0d18-a53e-4197-9c3c-e82331adc097")
                    @PathVariable
                    UUID sharedAlbumId,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @Parameter(
                            name = "Idempotency-Key",
                            in = ParameterIn.HEADER,
                            description = "일괄 삭제 재시도 식별자(UUID). 같은 요청 재시도에는 같은 값을 사용합니다.",
                            required = true,
                            schema = @Schema(type = "string", format = "uuid"),
                            example = "54cf8d7e-a23e-4e76-90f7-603f122b1507")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @RequestBody PhotoIdsRequest request) {
        return BaseResponse.success(
                PhotoSuccessCode.PHOTOS_DELETED,
                photoService.bulkDelete(sharedAlbumId, appUserId, idempotencyKey, request));
    }

    @Operation(
            summary = "공유집(앨범)에 기존 사진 추가",
            description =
                    "같은 공유 그룹에 속한 기존 사진을 요청 경로의 공유집(앨범)에 추가로 담습니다. 사진 원본을 새로 만들지 않고"
                            + " `shared_album_photo` 행만 추가하며, 이미 속한 사진은 건너뛰는 멱등 동작입니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "추가 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "INVALID_PHOTO_IDS"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "404", description = "SHARED_ALBUM_NOT_FOUND, PHOTO_NOT_FOUND"),
        @ApiResponse(responseCode = "409", description = "PHOTO_NOT_IN_SAME_SHARED_GROUP")
    })
    @PostMapping("/attach")
    public BaseResponse<PhotoAttachResponse> attachPhotos(
            @Parameter(description = "기존 사진을 추가할 공유집(앨범) 식별자", required = true, example = "59ce0d18-a53e-4197-9c3c-e82331adc097")
                    @PathVariable
                    UUID sharedAlbumId,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @Parameter(
                            name = "Idempotency-Key",
                            in = ParameterIn.HEADER,
                            description = "사진 추가 재시도 식별자(UUID). 같은 요청 재시도에는 같은 값을 사용합니다.",
                            required = true,
                            schema = @Schema(type = "string", format = "uuid"),
                            example = "54cf8d7e-a23e-4e76-90f7-603f122b1507")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @RequestBody PhotoIdsRequest request) {
        return BaseResponse.success(
                PhotoSuccessCode.PHOTOS_ATTACHED,
                photoService.attachPhotos(sharedAlbumId, appUserId, idempotencyKey, request));
    }

    @Operation(
            summary = "공유집(앨범)에서 사진 제거",
            description =
                    "해당 `shared_album_photo` 매핑을 즉시 물리 삭제합니다. 사진이 다른 공유집(앨범)에도 속해 있으면 원본은 유지되지만,"
                            + " 제거 대상 공유집(앨범)이 그 사진의 마지막 소속이면 매핑 제거와 함께 사진 원본도 soft delete됩니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "제거 성공", useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "INVALID_PHOTO_IDS"),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED"),
        @ApiResponse(responseCode = "404", description = "SHARED_ALBUM_NOT_FOUND")
    })
    @PostMapping("/detach")
    public BaseResponse<PhotoDetachResponse> detachPhotos(
            @Parameter(description = "사진을 분리할 공유집(앨범) 식별자", required = true, example = "59ce0d18-a53e-4197-9c3c-e82331adc097")
                    @PathVariable
                    UUID sharedAlbumId,
            @Parameter(hidden = true) @AuthenticationPrincipal UUID appUserId,
            @Parameter(
                            name = "Idempotency-Key",
                            in = ParameterIn.HEADER,
                            description = "사진 분리 재시도 식별자(UUID). 같은 요청 재시도에는 같은 값을 사용합니다.",
                            required = true,
                            schema = @Schema(type = "string", format = "uuid"),
                            example = "54cf8d7e-a23e-4e76-90f7-603f122b1507")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @RequestBody PhotoIdsRequest request) {
        return BaseResponse.success(
                PhotoSuccessCode.PHOTOS_DETACHED,
                photoService.detachPhotos(sharedAlbumId, appUserId, idempotencyKey, request));
    }
}
