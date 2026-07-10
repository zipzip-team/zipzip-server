package org.zipzip.zipzipserver.domain.album.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.zipzip.zipzipserver.domain.album.code.SharedAlbumErrorCode;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbum;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupMembershipRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupRepository;
import org.zipzip.zipzipserver.global.exception.BusinessException;

/** 공유집(앨범) API 전반에서 쓰는 인가 검증 지점(공유 그룹 존재·활성 멤버십, 공유집(앨범) 존재·활성 멤버십). */
@Component
@RequiredArgsConstructor
public class SharedAlbumAccessGuard {

    private final SharedGroupRepository sharedGroupRepository;
    private final SharedAlbumRepository sharedAlbumRepository;
    private final SharedGroupMembershipRepository sharedGroupMembershipRepository;

    public SharedGroup requireActiveSharedGroup(UUID sharedGroupId, UUID appUserId) {
        SharedGroup sharedGroup =
                sharedGroupRepository
                        .findById(sharedGroupId)
                        .filter(group -> group.getDeletedAt() == null)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                SharedAlbumErrorCode.SHARED_GROUP_NOT_FOUND));

        requireActiveMembership(sharedGroupId, appUserId);
        return sharedGroup;
    }

    public SharedAlbum requireActiveSharedAlbum(UUID sharedAlbumId, UUID appUserId) {
        SharedAlbum sharedAlbum =
                sharedAlbumRepository
                        .findById(sharedAlbumId)
                        .filter(album -> album.getDeletedAt() == null)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                SharedAlbumErrorCode.SHARED_ALBUM_NOT_FOUND));

        if (!sharedGroupMembershipRepository.existsActiveBySharedGroupIdAndAppUserId(
                sharedAlbum.getSharedGroup().getId(), appUserId)) {
            throw new BusinessException(SharedAlbumErrorCode.SHARED_ALBUM_NOT_FOUND);
        }
        return sharedAlbum;
    }

    private void requireActiveMembership(UUID sharedGroupId, UUID appUserId) {
        if (!sharedGroupMembershipRepository.existsActiveBySharedGroupIdAndAppUserId(
                sharedGroupId, appUserId)) {
            throw new BusinessException(SharedAlbumErrorCode.SHARED_GROUP_NOT_FOUND);
        }
    }
}
