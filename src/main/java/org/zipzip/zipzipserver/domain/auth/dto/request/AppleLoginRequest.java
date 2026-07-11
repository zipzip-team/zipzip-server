package org.zipzip.zipzipserver.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Apple 로그인 요청")
public record AppleLoginRequest(
        @Schema(
                        description =
                                "iOS ASAuthorizationAppleIDCredential.identityToken에서 받은 Apple ID Token(JWT)."
                                        + " Apple 사용자 식별, 서명·issuer·audience·만료·nonce 검증에 사용합니다.",
                        format = "JWT",
                        example = "eyJraWQiOi...",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                String identityToken,
        @Schema(
                        description =
                                "iOS ASAuthorizationAppleIDCredential.authorizationCode에서 받은 Apple 일회성"
                                        + " authorization code. 서버가 Apple 토큰 엔드포인트에 검증 요청할 때 사용하므로"
                                        + " 로그인 요청마다 새 값을 전달합니다.",
                        example = "c1a2b3...",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                String authorizationCode,
        @Schema(
                        description =
                                "iOS에서 Apple 로그인 요청을 만들 때 생성한 nonce 원문. identityToken의 nonce claim과"
                                        + " 정확히 일치해야 하며, 해시값이나 다른 로그인 시도의 nonce를 보내면 안 됩니다.",
                        example = "8f5d29f2-54e9-4da1-8a06-08e394af897e",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                String nonce,
        @Schema(
                        description =
                                "신규 가입 또는 탈퇴 계정 복구에 사용할 표시 이름. 기존 활성 사용자는 생략할 수 있으며,"
                                        + " 전달하면 앞뒤 공백을 제거한 1~50자여야 합니다.",
                        example = "집집이",
                        maxLength = 50)
                @Size(max = 50)
                String displayName) {}
