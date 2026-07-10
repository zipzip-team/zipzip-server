package org.zipzip.zipzipserver.domain.album.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbum;

public interface SharedAlbumRepository extends JpaRepository<SharedAlbum, UUID> {

    long deleteByDeletedAtIsNotNullAndDeletedAtLessThanEqual(Instant deletedAt);

    List<SharedAlbum> findBySharedGroupIdAndDeletedAtIsNull(UUID sharedGroupId);

    @Query(
            """
            select album
            from SharedAlbum album
            join fetch album.createdByAppUser
            where album.sharedGroup.id = :sharedGroupId
              and album.deletedAt is null
              and (cast(:cursorCreatedAt as timestamp) is null
                   or album.createdAt < :cursorCreatedAt
                   or (album.createdAt = :cursorCreatedAt and album.id < :cursorId))
            order by album.createdAt desc, album.id desc
            """)
    List<SharedAlbum> findPageByActiveSharedGroupId(
            @Param("sharedGroupId") UUID sharedGroupId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);
}
