package org.zipzip.zipzipserver.domain.album.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "공유집(앨범) 목록 조회 응답")
public record SharedAlbumListResponse(
        @Schema(description = "공유집(앨범) 목록 항목") List<Item> items,
        @Schema(description = "다음 페이지 cursor(불투명 문자열, 없으면 마지막 페이지)") String nextCursor,
        @Schema(description = "다음 페이지 존재 여부") boolean hasNext) {

    @Schema(description = "공유집(앨범) 목록 항목")
    public record Item(
            @Schema(description = "공유집(앨범) 식별자") UUID id,
            @Schema(description = "공유집(앨범) 이름", example = "제주도") String name,
            @Schema(description = "활성 매핑·활성 사진 기준 실시간 사진 수", example = "42") long photoCount,
            @Schema(
                            description =
                                    "썸네일이 준비된 사진 중 가장 먼저 저장된 순으로 최대 3장. READY가 아닌 사진은 건너뛰고 뒤의 READY"
                                            + " 사진으로 채우며, 준비된 사진이 부족하면 3장보다 적을 수 있습니다.")
                    List<Thumbnail> thumbnails,
            @Schema(description = "생성자 요약") SharedAlbumResponse.Creator createdBy,
            @Schema(description = "요청자 본인이 생성자인지 여부") boolean isCreator,
            @Schema(description = "생성 시각") Instant createdAt,
            @Schema(description = "수정 시각") Instant updatedAt) {}

    @Schema(description = "공유집(앨범) 목록 썸네일")
    public record Thumbnail(
            @Schema(description = "썸네일 presigned GET URL") String url,
            @Schema(description = "썸네일 URL 만료 시각") Instant urlExpiresAt) {}
}
