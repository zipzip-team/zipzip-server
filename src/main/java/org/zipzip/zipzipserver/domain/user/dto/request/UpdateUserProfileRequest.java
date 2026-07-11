package org.zipzip.zipzipserver.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 프로필 수정 요청")
public record UpdateUserProfileRequest(
        @Schema(
                        description =
                                "변경할 표시 이름. 서버가 앞뒤 공백을 제거한 뒤 1~50자인지 검증하며, 공백만"
                                        + " 있거나 50자를 초과하면 요청이 거부됩니다.",
                        example = "새 집집이",
                        minLength = 1,
                        maxLength = 50,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String displayName) {}
