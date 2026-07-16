package org.zipzip.zipzipserver.domain.sharedgroup.service;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.zipzip.zipzipserver.domain.photo.service.PhotoPurgeService;

/**
 * 30일 유예가 끝난 공유 그룹을 물리 삭제한다. 후보 확인과 최종 삭제에서만 짧게 행 잠금을 잡고, 사진은 그룹 트랜잭션 밖에서 순차 정리한다. 다중 인스턴스가 같은 그룹을
 * 중복 처리할 수 있지만 사진과 그룹 정리는 멱등하게 동작한다.
 */
@Service
@RequiredArgsConstructor
public class SharedGroupPurgeService {

    private final SharedGroupPurgeTransactionService transactionService;
    private final PhotoPurgeService photoPurgeService;

    public void purge(UUID sharedGroupId, Instant purgeBefore) {
        SharedGroupPurgeTarget target =
                transactionService.prepare(sharedGroupId, purgeBefore).orElse(null);
        if (target == null) {
            return;
        }

        for (UUID photoId : target.photoIds()) {
            photoPurgeService.purge(photoId);
        }

        transactionService.complete(sharedGroupId, purgeBefore, target.inviteCode());
    }
}
