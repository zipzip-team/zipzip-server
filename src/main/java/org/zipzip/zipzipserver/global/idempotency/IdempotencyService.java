package org.zipzip.zipzipserver.global.idempotency;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.zipzip.zipzipserver.global.code.GlobalErrorCode;
import org.zipzip.zipzipserver.global.code.SuccessCode;
import org.zipzip.zipzipserver.global.exception.BusinessException;
import org.zipzip.zipzipserver.global.response.BaseResponse;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String AUTH_REFRESH_CANONICAL_REQUEST_FORMAT =
            "{\"refreshTokenHash\":\"%s\"}";

    private final ApiIdempotencyRecordRepository apiIdempotencyRecordRepository;
    private final IdempotencyProperties properties;
    private final IdempotencyCrypto idempotencyCrypto;
    private final ObjectMapper objectMapper;
    private final Clock clock = Clock.systemUTC();

    public String hashAuthRefreshRequest(String refreshTokenHash) {
        return sha256Hex(AUTH_REFRESH_CANONICAL_REQUEST_FORMAT.formatted(refreshTokenHash));
    }

    public <T> IdempotencyStart<T> start(
            String scope,
            UUID idempotencyKey,
            String httpMethod,
            String apiPath,
            String requestHash,
            Class<T> responseType) {
        Instant now = Instant.now(clock);

        return apiIdempotencyRecordRepository
                .findWithLockByScopeAndIdempotencyKeyAndHttpMethodAndApiPath(
                        scope, idempotencyKey, httpMethod, apiPath)
                .map(record -> handleExistingRecord(record, requestHash, responseType, now))
                .orElseGet(
                        () ->
                                createProcessingRecord(
                                        scope,
                                        idempotencyKey,
                                        httpMethod,
                                        apiPath,
                                        requestHash,
                                        now));
    }

    public <T> void complete(ApiIdempotencyRecord record, SuccessCode successCode, T responseData) {
        try {
            BaseResponse<T> responseBody = BaseResponse.success(successCode, responseData);
            String responseJson = objectMapper.writeValueAsString(responseBody);
            record.complete(
                    successCode.getHttpStatus().value(), idempotencyCrypto.encrypt(responseJson));
        } catch (Exception exception) {
            throw new IllegalStateException("멱등성 응답 저장에 실패했습니다.", exception);
        }
    }

    private <T> IdempotencyStart<T> handleExistingRecord(
            ApiIdempotencyRecord record, String requestHash, Class<T> responseType, Instant now) {
        if (record.isExpired(now)) {
            apiIdempotencyRecordRepository.delete(record);
            apiIdempotencyRecordRepository.flush();
            ApiIdempotencyRecord newRecord =
                    ApiIdempotencyRecord.processing(
                            record.getScope(),
                            record.getIdempotencyKey(),
                            record.getHttpMethod(),
                            record.getApiPath(),
                            requestHash,
                            now.plus(properties.getAuthResponseTtl()));
            return new IdempotencyStart<>(
                    apiIdempotencyRecordRepository.saveAndFlush(newRecord), null, false);
        }

        if (!record.hasSameRequestHash(requestHash)) {
            throw new BusinessException(GlobalErrorCode.IDEMPOTENCY_KEY_REUSED);
        }

        if (record.getStatus() == ApiIdempotencyStatus.PROCESSING) {
            throw new BusinessException(GlobalErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
        }

        return new IdempotencyStart<>(record, decryptResponseData(record, responseType), true);
    }

    private <T> IdempotencyStart<T> createProcessingRecord(
            String scope,
            UUID idempotencyKey,
            String httpMethod,
            String apiPath,
            String requestHash,
            Instant now) {
        ApiIdempotencyRecord record =
                ApiIdempotencyRecord.processing(
                        scope,
                        idempotencyKey,
                        httpMethod,
                        apiPath,
                        requestHash,
                        now.plus(properties.getAuthResponseTtl()));
        try {
            return new IdempotencyStart<>(
                    apiIdempotencyRecordRepository.saveAndFlush(record), null, false);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(GlobalErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
        }
    }

    private <T> T decryptResponseData(ApiIdempotencyRecord record, Class<T> responseType) {
        try {
            JavaType type =
                    objectMapper
                            .getTypeFactory()
                            .constructParametricType(BaseResponse.class, responseType);
            BaseResponse<T> responseBody =
                    objectMapper.readValue(
                            idempotencyCrypto.decrypt(record.getEncryptedResponseBody()), type);
            return responseBody.data();
        } catch (Exception exception) {
            throw new IllegalStateException("멱등성 응답 복원에 실패했습니다.", exception);
        }
    }

    private String sha256Hex(String plainText) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(plainText.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("멱등성 요청 해시에 실패했습니다.", exception);
        }
    }

    public record IdempotencyStart<T>(
            ApiIdempotencyRecord record, T replayResponse, boolean replayed) {}
}
