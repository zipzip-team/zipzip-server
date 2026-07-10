package org.zipzip.zipzipserver.domain.photo.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumPhotoRepository;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.photo.repository.PhotoRepository;
import org.zipzip.zipzipserver.domain.reaction.repository.PhotoCommentRepository;
import org.zipzip.zipzipserver.domain.reaction.repository.PhotoLikeRepository;
import org.zipzip.zipzipserver.domain.storage.ObjectStorageService;

/**
 * 30일 유예가 끝난 soft-delete 사진의 물리 정리. 스토리지 객체 삭제가 성공한 경우에만 DB 행을 지운다(DB가 진실의 원천, 스토리지는 결과적 일관 — 4.6).
 * 매핑·댓글·좋아요는 사진 소프트 삭제 시점이 아니라 이 물리 정리 시점에 함께 지운다.
 */
@Service
@RequiredArgsConstructor
public class PhotoPurgeService {

    private final PhotoRepository photoRepository;
    private final SharedAlbumPhotoRepository sharedAlbumPhotoRepository;
    private final PhotoCommentRepository photoCommentRepository;
    private final PhotoLikeRepository photoLikeRepository;
    private final ObjectStorageService objectStorageService;

    @Transactional
    public void purge(UUID photoId) {
        Photo photo = photoRepository.findById(photoId).orElse(null);
        if (photo == null) {
            return;
        }

        List<String> objectKeys = new ArrayList<>();
        objectKeys.add(photo.getOriginalObjectKey());
        if (photo.getThumbnailObjectKey() != null) {
            objectKeys.add(photo.getThumbnailObjectKey());
        }
        objectStorageService.deleteAll(objectKeys);

        sharedAlbumPhotoRepository.deleteByPhotoId(photoId);
        sharedAlbumPhotoRepository.flush();
        photoCommentRepository.deleteByPhotoId(photoId);
        photoLikeRepository.deleteByPhotoId(photoId);
        photoRepository.delete(photo);
    }
}
