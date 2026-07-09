package org.zipzip.zipzipserver.global.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdempotencyRecordCleanupSchedulerTest {

    @Mock private ApiIdempotencyRecordRepository apiIdempotencyRecordRepository;

    @Test
    void 만료된_멱등성_레코드를_주기적으로_삭제한다() {
        IdempotencyRecordCleanupScheduler scheduler =
                new IdempotencyRecordCleanupScheduler(apiIdempotencyRecordRepository);
        Instant beforeCleanup = Instant.now();

        scheduler.deleteExpiredRecords();

        Instant afterCleanup = Instant.now();
        ArgumentCaptor<Instant> expiresAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(apiIdempotencyRecordRepository)
                .deleteByExpiresAtLessThanEqual(expiresAtCaptor.capture());
        assertThat(expiresAtCaptor.getValue()).isBetween(beforeCleanup, afterCleanup);
    }
}
