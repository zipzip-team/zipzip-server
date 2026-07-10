package org.zipzip.zipzipserver.domain.sharedgroup.service;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumPhotoRepository;
import org.zipzip.zipzipserver.domain.photo.service.PhotoPurgeService;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.InviteCodeReservationRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupRepository;

/**
 * 30일 유예가 끝난 공유 그룹을 물리 삭제한다. PostgreSQL 행 잠금으로 다중 인스턴스의 중복 처리를 막고, 사진의 Object Storage 정리가 모두 성공했을
 * 때만 그룹과 초대 코드 예약을 제거한다.
 */
@Service
@RequiredArgsConstructor
public class SharedGroupPurgeService {

    private final SharedGroupRepository sharedGroupRepository;
    private final SharedAlbumPhotoRepository sharedAlbumPhotoRepository;
    private final PhotoPurgeService photoPurgeService;
    private final InviteCodeReservationRepository inviteCodeReservationRepository;

    @Transactional
    public void purge(UUID sharedGroupId, Instant purgeBefore) {
        if (sharedGroupRepository.lockPurgeCandidateById(sharedGroupId, purgeBefore).isEmpty()) {
            return;
        }

        SharedGroup sharedGroup = sharedGroupRepository.getReferenceById(sharedGroupId);
        String inviteCode = sharedGroup.getInviteCodeReservation().getInviteCode();
        for (UUID photoId :
                sharedAlbumPhotoRepository.findDistinctPhotoIdsBySharedGroupId(sharedGroupId)) {
            photoPurgeService.purge(photoId);
        }

        sharedGroupRepository.delete(sharedGroup);
        sharedGroupRepository.flush();
        inviteCodeReservationRepository.deleteById(inviteCode);
    }
}
