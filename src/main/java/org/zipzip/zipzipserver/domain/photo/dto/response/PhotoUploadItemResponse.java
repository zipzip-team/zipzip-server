package org.zipzip.zipzipserver.domain.photo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "업로드 완료 등록된 사진")
public record PhotoUploadItemResponse(
        @Schema(description = "사진 식별자") UUID id,
        @Schema(description = "사진이 속한 공유 그룹 식별자") UUID sharedGroupId,
        @Schema(description = "사진이 등록된 공유집(앨범) 식별자") UUID sharedAlbumId,
        @Schema(description = "원본 이미지 presigned GET URL") String originalUrl,
        @Schema(description = "원본 URL 만료 시각") Instant originalUrlExpiresAt,
        @Schema(description = "썸네일 presigned GET URL. 생성 전이라 항상 null") String thumbnailUrl,
        @Schema(description = "썸네일 URL 만료 시각. 생성 전이라 항상 null") Instant thumbnailUrlExpiresAt,
        @Schema(description = "썸네일 생성 상태(등록 직후엔 항상 PENDING)", example = "PENDING")
                String thumbnailStatus,
        @Schema(description = "EXIF에서 추출한 촬영 기기명", example = "iPhone 15") String deviceModel,
        @Schema(description = "EXIF 촬영일시") Instant takenAt,
        @Schema(description = "촬영 위치 위도") Double latitude,
        @Schema(description = "촬영 위치 경도") Double longitude,
        @Schema(description = "촬영 위치명") String locationName,
        @Schema(description = "위치정보 추론 여부") boolean isInferred,
        @Schema(description = "이미지 가로 픽셀") Integer width,
        @Schema(description = "이미지 세로 픽셀") Integer height,
        @Schema(description = "사진 등록 시각") Instant createdAt) {}
