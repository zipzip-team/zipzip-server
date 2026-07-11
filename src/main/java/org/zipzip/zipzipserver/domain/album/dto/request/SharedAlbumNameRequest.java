package org.zipzip.zipzipserver.domain.album.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공유집(앨범) 이름 요청")
public record SharedAlbumNameRequest(
        @Schema(
                        description = "공유집(앨범) 이름. 서버가 앞뒤 공백을 제거한 뒤 1~100자인지 검증합니다.",
                        example = "제주도",
                        minLength = 1,
                        maxLength = 100,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String name) {}
