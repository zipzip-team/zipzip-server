package org.zipzip.zipzipserver.domain.chat.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;

@Schema(description = "채팅 또는 댓글 작성자 요약")
public record ChatAuthorResponse(
        @Schema(description = "사용자 식별자", example = "018f0c3e-2c77-7d72-a37e-2f5666f25d32")
                UUID userId,
        @Schema(description = "표시 이름", example = "집집이") String displayName) {

    public static ChatAuthorResponse from(AppUser appUser) {
        return new ChatAuthorResponse(appUser.getId(), appUser.getDisplayName());
    }
}
