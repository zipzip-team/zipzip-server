package org.zipzip.zipzipserver.global.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zipzip.zipzipserver.domain.auth.code.AuthSuccessCode;
import org.zipzip.zipzipserver.domain.auth.dto.response.TokenRefreshResponse;
import org.zipzip.zipzipserver.global.code.GlobalErrorCode;
import org.zipzip.zipzipserver.global.exception.BusinessException;
import org.zipzip.zipzipserver.global.response.BaseResponse;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    private static final String SCOPE = "AUTH_REFRESH:018f0c3e-2c77-7d72-a37e-2f5666f25d32";
    private static final UUID IDEMPOTENCY_KEY =
            UUID.fromString("54cf8d7e-a23e-4e76-90f7-603f122b1507");
    private static final String HTTP_METHOD = "POST";
    private static final String API_PATH = "/api/v1/auth/refresh";
    private static final String REQUEST_HASH = "a".repeat(64);

    @Mock private ApiIdempotencyRecordRepository apiIdempotencyRecordRepository;

    private IdempotencyService idempotencyService;
    private IdempotencyCrypto idempotencyCrypto;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        IdempotencyProperties properties = new IdempotencyProperties();
        properties.setAuthResponseEncryptionKey(
                "test-idempotency-secret-must-be-at-least-32-characters");
        properties.setAuthResponseTtl(Duration.ofMinutes(10));
        idempotencyCrypto = new IdempotencyCrypto(properties);
        objectMapper = new ObjectMapper();
        idempotencyService =
                new IdempotencyService(
                        apiIdempotencyRecordRepository,
                        properties,
                        idempotencyCrypto,
                        objectMapper);
    }

    @Test
    void 완료된_같은_요청은_저장된_응답을_복원한다() throws Exception {
        TokenRefreshResponse response =
                new TokenRefreshResponse("access-token", "refresh-token", "Bearer", 1800L);
        ApiIdempotencyRecord record = processingRecord();
        record.complete(
                200,
                idempotencyCrypto.encrypt(
                        objectMapper.writeValueAsString(
                                BaseResponse.success(
                                        AuthSuccessCode.AUTH_TOKEN_REFRESHED, response))));
        when(apiIdempotencyRecordRepository
                        .findWithLockByScopeAndIdempotencyKeyAndHttpMethodAndApiPath(
                                SCOPE, IDEMPOTENCY_KEY, HTTP_METHOD, API_PATH))
                .thenReturn(Optional.of(record));

        IdempotencyService.IdempotencyStart<TokenRefreshResponse> start =
                idempotencyService.start(
                        SCOPE,
                        IDEMPOTENCY_KEY,
                        HTTP_METHOD,
                        API_PATH,
                        REQUEST_HASH,
                        TokenRefreshResponse.class);

        assertThat(start.replayed()).isTrue();
        assertThat(start.replayResponse()).isEqualTo(response);
    }

    @Test
    void 같은_key의_다른_요청_본문은_거부한다() {
        ApiIdempotencyRecord record = processingRecord();
        when(apiIdempotencyRecordRepository
                        .findWithLockByScopeAndIdempotencyKeyAndHttpMethodAndApiPath(
                                SCOPE, IDEMPOTENCY_KEY, HTTP_METHOD, API_PATH))
                .thenReturn(Optional.of(record));

        assertIdempotencyError("b".repeat(64), GlobalErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void 같은_key의_처리중_요청은_거부한다() {
        ApiIdempotencyRecord record = processingRecord();
        when(apiIdempotencyRecordRepository
                        .findWithLockByScopeAndIdempotencyKeyAndHttpMethodAndApiPath(
                                SCOPE, IDEMPOTENCY_KEY, HTTP_METHOD, API_PATH))
                .thenReturn(Optional.of(record));

        assertIdempotencyError(REQUEST_HASH, GlobalErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
    }

    @Test
    void 기존_record가_없으면_PROCESSING_record를_생성한다() {
        when(apiIdempotencyRecordRepository
                        .findWithLockByScopeAndIdempotencyKeyAndHttpMethodAndApiPath(
                                SCOPE, IDEMPOTENCY_KEY, HTTP_METHOD, API_PATH))
                .thenReturn(Optional.empty());
        when(apiIdempotencyRecordRepository.saveAndFlush(any(ApiIdempotencyRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IdempotencyService.IdempotencyStart<TokenRefreshResponse> start =
                idempotencyService.start(
                        SCOPE,
                        IDEMPOTENCY_KEY,
                        HTTP_METHOD,
                        API_PATH,
                        REQUEST_HASH,
                        TokenRefreshResponse.class);

        assertThat(start.replayed()).isFalse();
        assertThat(start.record().getStatus()).isEqualTo(ApiIdempotencyStatus.PROCESSING);
        assertThat(start.record().getRequestHash()).isEqualTo(REQUEST_HASH);
    }

    private ApiIdempotencyRecord processingRecord() {
        return ApiIdempotencyRecord.processing(
                SCOPE,
                IDEMPOTENCY_KEY,
                HTTP_METHOD,
                API_PATH,
                REQUEST_HASH,
                Instant.parse("2999-01-01T00:00:00Z"));
    }

    private void assertIdempotencyError(String requestHash, GlobalErrorCode errorCode) {
        assertThatThrownBy(
                        () ->
                                idempotencyService.start(
                                        SCOPE,
                                        IDEMPOTENCY_KEY,
                                        HTTP_METHOD,
                                        API_PATH,
                                        requestHash,
                                        TokenRefreshResponse.class))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
