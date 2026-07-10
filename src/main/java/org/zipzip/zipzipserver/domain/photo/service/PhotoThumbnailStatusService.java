package org.zipzip.zipzipserver.domain.photo.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.photo.repository.PhotoRepository;

@Service
@RequiredArgsConstructor
public class PhotoThumbnailStatusService {

    private final PhotoRepository photoRepository;

    @Transactional
    public void markReady(UUID photoId, String thumbnailObjectKey) {
        photoRepository
                .findById(photoId)
                .ifPresent(photo -> photo.markThumbnailReady(thumbnailObjectKey));
    }

    @Transactional
    public void markFailed(UUID photoId) {
        photoRepository.findById(photoId).ifPresent(Photo::markThumbnailFailed);
    }
}
