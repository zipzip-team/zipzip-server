package org.zipzip.zipzipserver.domain.sharedgroup.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
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
import org.zipzip.zipzipserver.domain.photo.service.PhotoPurgeService;

@ExtendWith(MockitoExtension.class)
class SharedGroupPurgeServiceTest {

    private static final UUID SHARED_GROUP_ID = UUID.randomUUID();
    private static final Instant PURGE_BEFORE = Instant.parse("2026-07-16T00:00:00Z");
    private static final String INVITE_CODE = "ABC234EF";

    @Mock private SharedGroupPurgeTransactionService transactionService;
    @Mock private PhotoPurgeService photoPurgeService;

    @InjectMocks private SharedGroupPurgeService sharedGroupPurgeService;

    @Test
    void 정리_후보가_아니면_아무것도_정리하지_않는다() {
        when(transactionService.prepare(SHARED_GROUP_ID, PURGE_BEFORE))
                .thenReturn(Optional.empty());

        sharedGroupPurgeService.purge(SHARED_GROUP_ID, PURGE_BEFORE);

        verifyNoInteractions(photoPurgeService);
        verify(transactionService, never()).complete(SHARED_GROUP_ID, PURGE_BEFORE, INVITE_CODE);
    }

    @Test
    void 사진을_순차_정리한_뒤_공유_그룹_정리를_완료한다() {
        UUID firstPhotoId = UUID.randomUUID();
        UUID secondPhotoId = UUID.randomUUID();
        SharedGroupPurgeTarget target =
                new SharedGroupPurgeTarget(List.of(firstPhotoId, secondPhotoId), INVITE_CODE);
        when(transactionService.prepare(SHARED_GROUP_ID, PURGE_BEFORE))
                .thenReturn(Optional.of(target));

        sharedGroupPurgeService.purge(SHARED_GROUP_ID, PURGE_BEFORE);

        InOrder inOrder = inOrder(transactionService, photoPurgeService);
        inOrder.verify(transactionService).prepare(SHARED_GROUP_ID, PURGE_BEFORE);
        inOrder.verify(photoPurgeService).purge(firstPhotoId);
        inOrder.verify(photoPurgeService).purge(secondPhotoId);
        inOrder.verify(transactionService).complete(SHARED_GROUP_ID, PURGE_BEFORE, INVITE_CODE);
    }

    @Test
    void 사진_정리에_실패하면_공유_그룹을_삭제하지_않는다() {
        UUID firstPhotoId = UUID.randomUUID();
        UUID secondPhotoId = UUID.randomUUID();
        SharedGroupPurgeTarget target =
                new SharedGroupPurgeTarget(List.of(firstPhotoId, secondPhotoId), INVITE_CODE);
        when(transactionService.prepare(SHARED_GROUP_ID, PURGE_BEFORE))
                .thenReturn(Optional.of(target));
        doNothing()
                .doThrow(new IllegalStateException("Object Storage unavailable"))
                .when(photoPurgeService)
                .purge(any());

        assertThatThrownBy(() -> sharedGroupPurgeService.purge(SHARED_GROUP_ID, PURGE_BEFORE))
                .isInstanceOf(IllegalStateException.class);

        verify(photoPurgeService).purge(firstPhotoId);
        verify(transactionService, never()).complete(SHARED_GROUP_ID, PURGE_BEFORE, INVITE_CODE);
    }
}
