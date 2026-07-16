package org.zipzip.zipzipserver.domain.sharedgroup.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumPhotoRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.InviteCodeReservationRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupRepository;

/** 공유 그룹 정리 과정에서 PostgreSQL 행 잠금이 필요한 짧은 트랜잭션을 담당한다. */
@Service
@RequiredArgsConstructor
public class SharedGroupPurgeTransactionService {

    private final SharedGroupRepository sharedGroupRepository;
    private final SharedAlbumPhotoRepository sharedAlbumPhotoRepository;
    private final InviteCodeReservationRepository inviteCodeReservationRepository;

    @Transactional
    public Optional<SharedGroupPurgeTarget> prepare(UUID sharedGroupId, Instant purgeBefore) {
        SharedGroup sharedGroup =
                sharedGroupRepository
                        .lockPurgeCandidateById(sharedGroupId, purgeBefore)
                        .orElse(null);
        if (sharedGroup == null) {
            return Optional.empty();
        }

        String inviteCode = sharedGroup.getInviteCodeReservation().getInviteCode();
        List<UUID> photoIds =
                sharedAlbumPhotoRepository.findDistinctPhotoIdsBySharedGroupId(sharedGroupId);
        return Optional.of(new SharedGroupPurgeTarget(photoIds, inviteCode));
    }

    @Transactional
    public void complete(UUID sharedGroupId, Instant purgeBefore, String inviteCode) {
        SharedGroup sharedGroup =
                sharedGroupRepository
                        .lockPurgeCandidateById(sharedGroupId, purgeBefore)
                        .orElse(null);
        if (sharedGroup == null) {
            return;
        }

        if (!sharedAlbumPhotoRepository
                .findDistinctPhotoIdsBySharedGroupId(sharedGroupId)
                .isEmpty()) {
            return;
        }

        sharedGroupRepository.delete(sharedGroup);
        sharedGroupRepository.flush();
        inviteCodeReservationRepository.deleteById(inviteCode);
    }
}

record SharedGroupPurgeTarget(List<UUID> photoIds, String inviteCode) {

    SharedGroupPurgeTarget {
        photoIds = List.copyOf(photoIds);
    }
}
