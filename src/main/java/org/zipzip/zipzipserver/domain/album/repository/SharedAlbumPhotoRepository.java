package org.zipzip.zipzipserver.domain.album.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbumPhoto;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;

public interface SharedAlbumPhotoRepository extends JpaRepository<SharedAlbumPhoto, UUID> {

    List<SharedAlbumPhoto> findByPhotoId(UUID photoId);

    void deleteByPhotoId(UUID photoId);

    boolean existsBySharedAlbumIdAndPhotoId(UUID sharedAlbumId, UUID photoId);

    Optional<SharedAlbumPhoto> findBySharedAlbumIdAndPhotoId(UUID sharedAlbumId, UUID photoId);

    long countByPhotoId(UUID photoId);

    long countBySharedAlbumIdAndPhotoDeletedAtIsNull(UUID sharedAlbumId);

    List<SharedAlbumPhoto> findBySharedAlbumId(UUID sharedAlbumId);

    void deleteBySharedAlbumId(UUID sharedAlbumId);

    @Query(
            """
            select photo
            from SharedAlbumPhoto sap
            join sap.photo photo
            join fetch photo.uploadedByAppUser
            where sap.sharedAlbum.id = :sharedAlbumId
              and photo.deletedAt is null
              and (cast(:cursorDisplayAt as timestamp) is null
                   or coalesce(photo.takenAt, photo.createdAt) < :cursorDisplayAt
                   or (coalesce(photo.takenAt, photo.createdAt) = :cursorDisplayAt
                       and photo.id < :cursorId))
            order by coalesce(photo.takenAt, photo.createdAt) desc, photo.id desc
            """)
    List<Photo> findPageByActiveSharedAlbumId(
            @Param("sharedAlbumId") UUID sharedAlbumId,
            @Param("cursorDisplayAt") Instant cursorDisplayAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);
}
