package org.zipzip.zipzipserver.domain.reaction.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.zipzip.zipzipserver.domain.reaction.entity.PhotoComment;

public interface PhotoCommentRepository extends JpaRepository<PhotoComment, UUID> {

    @Modifying(flushAutomatically = true)
    @Query("delete from PhotoComment photoComment where photoComment.photo.id = :photoId")
    void deleteByPhotoId(@Param("photoId") UUID photoId);

    long countByPhotoId(UUID photoId);

    @Query(
            """
            select photoComment
            from PhotoComment photoComment
            join fetch photoComment.appUser
            where photoComment.photo.id = :photoId
              and (cast(:cursorCreatedAt as timestamp) is null
                   or photoComment.createdAt > :cursorCreatedAt
                   or (photoComment.createdAt = :cursorCreatedAt
                       and photoComment.id > :cursorId))
            order by photoComment.createdAt asc, photoComment.id asc
            """)
    List<PhotoComment> findPageByPhotoId(
            @Param("photoId") UUID photoId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);
}
