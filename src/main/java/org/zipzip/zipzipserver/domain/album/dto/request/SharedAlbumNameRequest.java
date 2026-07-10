package org.zipzip.zipzipserver.domain.album.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공유집(앨범) 이름 요청")
public record SharedAlbumNameRequest(
        @Schema(description = "공유집(앨범) 이름(trim 후 1~100자)", example = "제주도") String name) {}
