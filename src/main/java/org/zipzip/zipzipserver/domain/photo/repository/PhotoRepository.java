package org.zipzip.zipzipserver.domain.photo.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.photo.entity.PhotoThumbnailStatus;

public interface PhotoRepository extends JpaRepository<Photo, UUID> {

    List<Photo> findByDeletedAtIsNullAndThumbnailStatusInAndUpdatedAtLessThanEqual(
            List<PhotoThumbnailStatus> thumbnailStatuses, Instant updatedAt);

    List<Photo> findByDeletedAtIsNotNullAndDeletedAtLessThanEqual(Instant deletedAt);
}
