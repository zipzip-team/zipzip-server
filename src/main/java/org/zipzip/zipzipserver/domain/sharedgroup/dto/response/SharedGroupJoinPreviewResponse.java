package org.zipzip.zipzipserver.domain.sharedgroup.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;

@Schema(description = "초대 코드 기반 공유 그룹 참여 미리보기 응답")
public record SharedGroupJoinPreviewResponse(
        @Schema(description = "참여 대상 공유 그룹 식별자", example = "b8a5f612-25d7-4ec3-9d1d-59684de40664")
                UUID sharedGroupId,
        @Schema(description = "공유 그룹 이름", example = "우리 집") String name,
        @Schema(description = "그룹 내 최신 활성 사진의 대표 이미지 presigned URL. 사진이 없으면 null", nullable = true)
                String representativeImageUrl,
        @Schema(description = "대표 이미지 URL 만료 시각. 대표 이미지가 없으면 null", nullable = true)
                Instant representativeImageUrlExpiresAt,
        @Schema(description = "공유 그룹 최초 생성자") SharedGroupUserSummaryResponse createdBy,
        @Schema(description = "활성 멤버 수", example = "4") long memberCount,
        @Schema(description = "가입 순서 기준 앞 5명의 활성 멤버 프로필") List<Member> members,
        @Schema(description = "요청자가 이미 활성 멤버인지 여부. true여도 미리보기는 정상 반환합니다.", example = "false")
                boolean alreadyJoined) {

    @Schema(description = "공유 그룹 참여 미리보기 멤버 프로필")
    public record Member(
            @Schema(description = "멤버 사용자 식별자", example = "018f0c3e-2c77-7d72-a37e-2f5666f25d32")
                    UUID userId,
            @Schema(description = "멤버 표시 이름", example = "집집이") String displayName,
            @Schema(description = "멤버의 현재 공유 그룹 역할", example = "HOST") SharedGroupRole role) {}
}
