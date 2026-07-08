package org.zipzip.zipzipserver.domain.auth.apple;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.zipzip.zipzipserver.domain.auth.code.AuthErrorCode;
import org.zipzip.zipzipserver.domain.auth.config.AppleOAuthProperties;
import org.zipzip.zipzipserver.global.exception.BusinessException;

@Component
@RequiredArgsConstructor
public class AppleTokenClient {

    private static final String APPLE_TOKEN_URL = "https://appleid.apple.com/auth/token";
    private static final String GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code";

    private final AppleOAuthProperties properties;
    private final AppleClientSecretGenerator clientSecretGenerator;

    private final RestClient restClient = RestClient.create();

    public AppleTokenResponse requestToken(String authorizationCode) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.getClientId());
        form.add("client_secret", clientSecretGenerator.generate());
        form.add("code", authorizationCode);
        form.add("grant_type", GRANT_TYPE_AUTHORIZATION_CODE);

        try {
            AppleTokenResponse response =
                    restClient
                            .post()
                            .uri(APPLE_TOKEN_URL)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(form)
                            .retrieve()
                            .body(AppleTokenResponse.class);

            if (response == null || response.idToken() == null || response.idToken().isBlank()) {
                throw new BusinessException(AuthErrorCode.INVALID_APPLE_AUTHORIZATION_CODE);
            }

            return response;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new BusinessException(AuthErrorCode.INVALID_APPLE_AUTHORIZATION_CODE);
        }
    }
}
