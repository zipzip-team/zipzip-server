package org.zipzip.zipzipserver.domain.album.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "공유집(앨범) 생성/상세 응답")
public record SharedAlbumResponse(
        @Schema(description = "공유집(앨범) 식별자") UUID id,
        @Schema(description = "소속 공유 그룹 식별자") UUID sharedGroupId,
        @Schema(description = "공유집(앨범) 이름", example = "제주도") String name,
        @Schema(description = "활성 매핑·활성 사진 기준 실시간 사진 수", example = "42") long photoCount,
        @Schema(description = "생성자 요약") Creator createdBy,
        @Schema(description = "요청자 본인이 생성자인지 여부") boolean isCreator,
        @Schema(description = "생성 시각") Instant createdAt,
        @Schema(description = "수정 시각") Instant updatedAt) {

    @Schema(description = "생성자 요약")
    public record Creator(
            @Schema(description = "생성자 사용자 식별자") UUID userId,
            @Schema(description = "생성자 표시 이름", example = "집집이") String displayName) {}
}
