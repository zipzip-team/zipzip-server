package org.zipzip.zipzipserver.global.idempotency;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.zipzip.zipzipserver.global.code.GlobalErrorCode;
import org.zipzip.zipzipserver.global.exception.BusinessException;

public final class IdempotencyResponseSupport {

    private static final String REPLAYED_HEADER = "Idempotency-Replayed";

    private IdempotencyResponseSupport() {}

    public static UUID parseKey(String idempotencyKeyHeader) {
        try {
            return UUID.fromString(idempotencyKeyHeader);
        } catch (Exception exception) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }
    }

    public static ResponseEntity<?> toResponse(IdempotencyResult result) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.statusCode());
        if (result.replayed()) {
            response.header(REPLAYED_HEADER, "true");
        }
        return response.body(result.body());
    }
}
