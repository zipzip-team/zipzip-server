package org.zipzip.zipzipserver.domain.auth.apple;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
    private static final Duration APPLE_TOKEN_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration APPLE_TOKEN_READ_TIMEOUT = Duration.ofSeconds(5);

    private final AppleOAuthProperties properties;
    private final AppleClientSecretGenerator clientSecretGenerator;

    private final RestClient restClient =
            RestClient.builder().requestFactory(createRequestFactory()).build();

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

    private static SimpleClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(APPLE_TOKEN_CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(APPLE_TOKEN_READ_TIMEOUT);
        return requestFactory;
    }
}
