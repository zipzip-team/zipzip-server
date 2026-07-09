package org.zipzip.zipzipserver.global.idempotency;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiIdempotencyRecordRepository extends JpaRepository<ApiIdempotencyRecord, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select record
            from ApiIdempotencyRecord record
            where record.scope = :scope
              and record.idempotencyKey = :idempotencyKey
              and record.httpMethod = :httpMethod
              and record.apiPath = :apiPath
            """)
    Optional<ApiIdempotencyRecord> findWithLockByScopeAndIdempotencyKeyAndHttpMethodAndApiPath(
            @Param("scope") String scope,
            @Param("idempotencyKey") UUID idempotencyKey,
            @Param("httpMethod") String httpMethod,
            @Param("apiPath") String apiPath);
}
