package org.zipzip.zipzipserver.domain.sharedgroup.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "공유 그룹 초대 코드 조회 응답")
public record InviteCodeResponse(
        @Schema(description = "공유 그룹 식별자", example = "b8a5f612-25d7-4ec3-9d1d-59684de40664") UUID sharedGroupId,
        @Schema(description = "공유 그룹 생성 시 발급되어 유지되는 초대 코드", example = "ZZ7K9P2Q") String inviteCode) {}
