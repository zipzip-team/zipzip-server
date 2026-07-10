package org.zipzip.zipzipserver.domain.photo.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbum;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbumPhoto;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumPhotoRepository;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumRepository;
import org.zipzip.zipzipserver.domain.photo.code.PhotoErrorCode;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupMembershipRepository;
import org.zipzip.zipzipserver.global.exception.BusinessException;

/**
 * 사진·공유집(앨범) API 전반에서 쓰는 인가 검증 지점. 공유 그룹 생성·초대·입장 같은 쓰기 API(04/05/06 문서)는 이번 범위 밖이라 별도로 없고, 이 클래스가
 * 그 대신 필요한 최소 읽기 전용 조회(공유집 존재, 활성 멤버십)만 담당한다.
 */
@Component
@RequiredArgsConstructor
public class PhotoAccessGuard {

    private final SharedAlbumRepository sharedAlbumRepository;
    private final SharedAlbumPhotoRepository sharedAlbumPhotoRepository;
    private final SharedGroupMembershipRepository sharedGroupMembershipRepository;

    public SharedAlbum requireActiveSharedAlbum(UUID sharedAlbumId, UUID appUserId) {
        SharedAlbum sharedAlbum =
                sharedAlbumRepository
                        .findById(sharedAlbumId)
                        .filter(album -> album.getDeletedAt() == null)
                        .orElseThrow(
                                () -> new BusinessException(PhotoErrorCode.SHARED_ALBUM_NOT_FOUND));

        if (!sharedGroupMembershipRepository.existsActiveBySharedGroupIdAndAppUserId(
                sharedAlbum.getSharedGroup().getId(), appUserId)) {
            throw new BusinessException(PhotoErrorCode.SHARED_ALBUM_NOT_FOUND);
        }
        return sharedAlbum;
    }

    public void requireActivePhotoAccess(Photo photo, UUID appUserId) {
        UUID sharedGroupId = resolveActiveSharedGroupId(photo);

        if (!sharedGroupMembershipRepository.existsActiveBySharedGroupIdAndAppUserId(
                sharedGroupId, appUserId)) {
            throw new BusinessException(PhotoErrorCode.PHOTO_NOT_FOUND);
        }
    }

    /** 사진이 현재 속한 (soft-delete 되지 않은) 공유집(앨범)을 통해 공유 그룹을 역산한다. */
    public UUID resolveActiveSharedGroupId(Photo photo) {
        return sharedAlbumPhotoRepository.findByPhotoId(photo.getId()).stream()
                .map(SharedAlbumPhoto::getSharedAlbum)
                .filter(album -> album.getDeletedAt() == null)
                .map(album -> album.getSharedGroup().getId())
                .findFirst()
                .orElseThrow(() -> new BusinessException(PhotoErrorCode.PHOTO_NOT_FOUND));
    }
}
