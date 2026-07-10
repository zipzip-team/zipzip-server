package org.zipzip.zipzipserver.domain.sharedgroup.scheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.service.SharedGroupPurgeService;

/** 30일 유예가 끝난 공유 그룹과 하위 사진을 스토리지부터 안전하게 정리한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SharedGroupSweepScheduler {

    private static final Duration SHARED_GROUP_PURGE_GRACE_PERIOD = Duration.ofDays(30);
    private static final int PURGE_BATCH_SIZE = 100;

    private final SharedGroupRepository sharedGroupRepository;
    private final SharedGroupPurgeService sharedGroupPurgeService;
    private final Clock clock = Clock.systemUTC();

    @Scheduled(fixedDelayString = "${shared-group.sweep.purge-interval:PT1H}")
    public void purgeOldSoftDeletedSharedGroups() {
        Instant purgeBefore = Instant.now(clock).minus(SHARED_GROUP_PURGE_GRACE_PERIOD);
        for (UUID sharedGroupId :
                sharedGroupRepository.findPurgeCandidateIds(purgeBefore, PURGE_BATCH_SIZE)) {
            try {
                sharedGroupPurgeService.purge(sharedGroupId, purgeBefore);
            } catch (Exception exception) {
                log.warn(
                        "[SharedGroupSweep] 공유 그룹 물리 삭제 실패 sharedGroupId={}",
                        sharedGroupId,
                        exception);
            }
        }
    }
}
