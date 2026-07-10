package org.zipzip.zipzipserver.domain.album.scheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumRepository;

/**
 * 30일 유예가 끝난 soft-delete 공유집(앨범) 행을 물리 삭제한다(ALBUM-05). 삭제 시점에 이미 {@code shared_album_photo} 매핑은 즉시
 * 물리 삭제됐고, 함께 soft delete된 사진은 {@code PhotoSweepScheduler}가 각자 정리하므로 여기서는 {@code shared_album} 행만
 * 지우면 된다.
 */
@Component
@RequiredArgsConstructor
public class SharedAlbumSweepScheduler {

    private static final Duration SHARED_ALBUM_PURGE_GRACE_PERIOD = Duration.ofDays(30);

    private final SharedAlbumRepository sharedAlbumRepository;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    @Scheduled(fixedDelayString = "${shared-album.sweep.purge-interval:PT1H}")
    public void purgeOldSoftDeletedAlbums() {
        Instant purgeBefore = Instant.now(clock).minus(SHARED_ALBUM_PURGE_GRACE_PERIOD);
        sharedAlbumRepository.deleteByDeletedAtIsNotNullAndDeletedAtLessThanEqual(purgeBefore);
    }
}
