package org.zipzip.zipzipserver.global.idempotency;

import java.util.UUID;
import org.zipzip.zipzipserver.global.code.GlobalErrorCode;
import org.zipzip.zipzipserver.global.exception.BusinessException;

public final class IdempotencyResponseSupport {

    private IdempotencyResponseSupport() {}

    public static UUID parseKey(String idempotencyKeyHeader) {
        try {
            return UUID.fromString(idempotencyKeyHeader);
        } catch (Exception exception) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }
    }
}
