package org.zipzip.zipzipserver.global.idempotency;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class IdempotencyRecordCleanupScheduler {

    private final ApiIdempotencyRecordRepository apiIdempotencyRecordRepository;

    @Transactional
    @Scheduled(fixedDelayString = "${idempotency.cleanup-interval:PT1H}")
    public void deleteExpiredRecords() {
        apiIdempotencyRecordRepository.deleteByExpiresAtLessThanEqual(Instant.now());
    }
}
