package org.zipzip.zipzipserver.domain.photo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "공유집(앨범) 사진 목록 조회 응답")
public record PhotoListResponse(
        @Schema(description = "사진 목록 항목") List<PhotoListItem> items,
        @Schema(description = "다음 페이지 cursor(불투명 문자열, 없으면 마지막 페이지)") String nextCursor,
        @Schema(description = "다음 페이지 존재 여부") boolean hasNext) {

    @Schema(description = "사진 목록 항목")
    public record PhotoListItem(
            @Schema(description = "사진 식별자") UUID id,
            @Schema(description = "사진이 속한 공유 그룹 식별자(소속 공유집을 통해 조회 시점에 계산)") UUID sharedGroupId,
            @Schema(description = "사진이 속한 공유집(앨범) 식별자") UUID sharedAlbumId,
            @Schema(description = "원본 이미지 presigned GET URL") String originalUrl,
            @Schema(description = "원본 URL 만료 시각") Instant originalUrlExpiresAt,
            @Schema(description = "썸네일 presigned GET URL. thumbnailStatus가 READY가 아니면 null")
                    String thumbnailUrl,
            @Schema(description = "썸네일 URL 만료 시각. 썸네일이 없으면 null") Instant thumbnailUrlExpiresAt,
            @Schema(description = "썸네일 생성 상태", example = "READY") String thumbnailStatus,
            @Schema(description = "EXIF에서 추출한 촬영 기기명", example = "iPhone 15") String deviceModel,
            @Schema(description = "EXIF 촬영일시. 없으면 null") Instant takenAt,
            @Schema(description = "표시·정렬 기준 시각(takenAt이 없으면 createdAt)") Instant displayAt,
            @Schema(description = "촬영 위치 위도") Double latitude,
            @Schema(description = "촬영 위치 경도") Double longitude,
            @Schema(description = "촬영 위치명") String locationName,
            @Schema(description = "위치정보 추론 여부") boolean isInferred,
            @Schema(description = "이미지 가로 픽셀") Integer width,
            @Schema(description = "이미지 세로 픽셀") Integer height,
            @Schema(description = "업로더 요약") Uploader uploadedBy,
            @Schema(description = "요청자 본인이 업로더인지 여부") boolean isUploader,
            @Schema(description = "좋아요 수(현재 범위에서는 항상 0)", example = "0") long likeCount,
            @Schema(description = "댓글 수(현재 범위에서는 항상 0)", example = "0") long commentCount,
            @Schema(description = "요청자의 좋아요 여부(현재 범위에서는 항상 false)") boolean isLikedByMe,
            @Schema(description = "사진 등록 시각") Instant createdAt) {}

    @Schema(description = "업로더 요약")
    public record Uploader(
            @Schema(description = "업로더 사용자 식별자") UUID userId,
            @Schema(description = "업로더 표시 이름", example = "집집이") String displayName) {}
}
