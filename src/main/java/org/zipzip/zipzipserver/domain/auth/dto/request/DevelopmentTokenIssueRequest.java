package org.zipzip.zipzipserver.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "개발 환경 토큰 발급 요청")
public record DevelopmentTokenIssueRequest(
        @Schema(
                        description = "개발용 사용자를 식별하는 키. 같은 키로 요청하면 기존 개발용 사용자의 토큰을 새로 발급합니다.",
                        example = "ios-tester-1",
                        minLength = 1,
                        maxLength = 50,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 50)
                @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*")
                String testUserKey,
        @Schema(
                        description = "새 개발용 사용자를 만들거나 탈퇴 상태를 복구할 때 사용할 표시 이름",
                        example = "iOS 테스트 사용자",
                        minLength = 1,
                        maxLength = 50,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @Size(max = 50)
                String displayName) {}
