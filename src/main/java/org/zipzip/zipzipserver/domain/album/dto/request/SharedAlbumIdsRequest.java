package org.zipzip.zipzipserver.domain.album.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "공유집(앨범) 식별자 목록 요청(중복 없이 1~100개)")
public record SharedAlbumIdsRequest(
        @Schema(
                        description = "삭제할 공유집(앨범) 식별자 목록. 중복 없이 1~100개를 전달합니다.",
                        example =
                                "[\"59ce0d18-a53e-4197-9c3c-e82331adc097\",\"b8a5f612-25d7-4ec3-9d1d-59684de40664\"]",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                List<UUID> sharedAlbumIds) {}
