package org.zipzip.zipzipserver.domain.album.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "공유집(앨범) 이름 수정 응답")
public record SharedAlbumRenameResponse(
        @Schema(description = "공유집(앨범) 식별자") UUID id,
        @Schema(description = "수정된 이름", example = "제주 여름") String name,
        @Schema(description = "수정 시각") Instant updatedAt) {}
