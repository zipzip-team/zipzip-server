package org.zipzip.zipzipserver.domain.album.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbumPhoto;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;

public interface SharedAlbumPhotoRepository extends JpaRepository<SharedAlbumPhoto, UUID> {

    List<SharedAlbumPhoto> findByPhotoId(UUID photoId);

    @Modifying(flushAutomatically = true)
    @Query("delete from SharedAlbumPhoto mapping where mapping.photo.id = :photoId")
    void deleteByPhotoId(@Param("photoId") UUID photoId);

    boolean existsBySharedAlbumIdAndPhotoId(UUID sharedAlbumId, UUID photoId);

    Optional<SharedAlbumPhoto> findBySharedAlbumIdAndPhotoId(UUID sharedAlbumId, UUID photoId);

    long countByPhotoId(UUID photoId);

    long countBySharedAlbumIdAndPhotoDeletedAtIsNull(UUID sharedAlbumId);

    List<SharedAlbumPhoto> findBySharedAlbumId(UUID sharedAlbumId);

    @Query(
            """
            select distinct mapping.photo
            from SharedAlbumPhoto mapping
            join mapping.sharedAlbum album
            where album.sharedGroup.id = :sharedGroupId
              and mapping.photo.deletedAt is null
            """)
    List<Photo> findActivePhotosBySharedGroupId(@Param("sharedGroupId") UUID sharedGroupId);

    @Query(
            """
            select photo
            from SharedAlbumPhoto mapping
            join mapping.sharedAlbum album
            join mapping.photo photo
            where album.sharedGroup.id = :sharedGroupId
              and album.deletedAt is null
              and photo.deletedAt is null
            order by coalesce(photo.takenAt, photo.createdAt) desc, photo.id desc
            """)
    List<Photo> findRepresentativePhotoBySharedGroupId(
            @Param("sharedGroupId") UUID sharedGroupId, Pageable pageable);

    @Query(
            """
            select distinct mapping.photo.id
            from SharedAlbumPhoto mapping
            where mapping.sharedAlbum.sharedGroup.id = :sharedGroupId
            """)
    List<UUID> findDistinctPhotoIdsBySharedGroupId(@Param("sharedGroupId") UUID sharedGroupId);

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
