package org.zipzip.zipzipserver.domain.sharedgroup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumPhotoRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.InviteCodeReservation;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.InviteCodeReservationRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupRepository;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;

@ExtendWith(MockitoExtension.class)
class SharedGroupPurgeTransactionServiceTest {

    private static final Instant PURGE_BEFORE = Instant.parse("2026-07-16T00:00:00Z");
    private static final String INVITE_CODE = "ABC234EF";

    @Mock private SharedGroupRepository sharedGroupRepository;
    @Mock private SharedAlbumPhotoRepository sharedAlbumPhotoRepository;
    @Mock private InviteCodeReservationRepository inviteCodeReservationRepository;

    @InjectMocks private SharedGroupPurgeTransactionService transactionService;

    @Test
    void 잠글_수_없는_후보는_준비하지_않는다() {
        UUID sharedGroupId = UUID.randomUUID();
        when(sharedGroupRepository.lockPurgeCandidateById(sharedGroupId, PURGE_BEFORE))
                .thenReturn(Optional.empty());

        Optional<SharedGroupPurgeTarget> target =
                transactionService.prepare(sharedGroupId, PURGE_BEFORE);

        assertThat(target).isEmpty();
        verifyNoInteractions(sharedAlbumPhotoRepository);
    }

    @Test
    void 잠근_후보에서_사진_ID와_초대_코드를_수집한다() {
        SharedGroup sharedGroup = aSharedGroup();
        UUID firstPhotoId = UUID.randomUUID();
        UUID secondPhotoId = UUID.randomUUID();
        when(sharedGroupRepository.lockPurgeCandidateById(sharedGroup.getId(), PURGE_BEFORE))
                .thenReturn(Optional.of(sharedGroup));
        when(sharedAlbumPhotoRepository.findDistinctPhotoIdsBySharedGroupId(sharedGroup.getId()))
                .thenReturn(List.of(firstPhotoId, secondPhotoId));

        SharedGroupPurgeTarget target =
                transactionService.prepare(sharedGroup.getId(), PURGE_BEFORE).orElseThrow();

        assertThat(target.photoIds()).containsExactly(firstPhotoId, secondPhotoId);
        assertThat(target.inviteCode()).isEqualTo(INVITE_CODE);
    }

    @Test
    void 남은_사진_매핑이_있으면_공유_그룹을_삭제하지_않는다() {
        SharedGroup sharedGroup = aSharedGroup();
        when(sharedGroupRepository.lockPurgeCandidateById(sharedGroup.getId(), PURGE_BEFORE))
                .thenReturn(Optional.of(sharedGroup));
        when(sharedAlbumPhotoRepository.findDistinctPhotoIdsBySharedGroupId(sharedGroup.getId()))
                .thenReturn(List.of(UUID.randomUUID()));

        transactionService.complete(sharedGroup.getId(), PURGE_BEFORE, INVITE_CODE);

        verify(sharedGroupRepository, never()).flush();
        verifyNoInteractions(inviteCodeReservationRepository);
    }

    @Test
    void 남은_사진이_없으면_그룹_삭제를_반영한_뒤_초대_코드를_삭제한다() {
        SharedGroup sharedGroup = aSharedGroup();
        when(sharedGroupRepository.lockPurgeCandidateById(sharedGroup.getId(), PURGE_BEFORE))
                .thenReturn(Optional.of(sharedGroup));
        when(sharedAlbumPhotoRepository.findDistinctPhotoIdsBySharedGroupId(sharedGroup.getId()))
                .thenReturn(List.of());
        transactionService.complete(sharedGroup.getId(), PURGE_BEFORE, INVITE_CODE);

        InOrder inOrder = inOrder(sharedGroupRepository, inviteCodeReservationRepository);
        inOrder.verify(sharedGroupRepository).delete(sharedGroup);
        inOrder.verify(sharedGroupRepository).flush();
        inOrder.verify(inviteCodeReservationRepository).deleteById(INVITE_CODE);
    }

    private SharedGroup aSharedGroup() {
        AppUser creator = AppUser.create("apple-subject-" + UUID.randomUUID(), "집집이");
        return SharedGroup.create(creator, "삭제된 그룹", InviteCodeReservation.create(INVITE_CODE));
    }
}
