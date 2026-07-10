package org.zipzip.zipzipserver.domain.reaction.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.zipzip.zipzipserver.domain.reaction.entity.PhotoLike;

public interface PhotoLikeRepository extends JpaRepository<PhotoLike, UUID> {

    void deleteByAppUserId(UUID appUserId);

    @Modifying(flushAutomatically = true)
    @Query("delete from PhotoLike photoLike where photoLike.photo.id = :photoId")
    void deleteByPhotoId(@Param("photoId") UUID photoId);

    boolean existsByPhotoIdAndAppUserId(UUID photoId, UUID appUserId);

    long countByPhotoId(UUID photoId);

    long deleteByPhotoIdAndAppUserId(UUID photoId, UUID appUserId);

    @Modifying
    @Query(
            value =
                    """
                    insert into photo_like (id, photo_id, app_user_id)
                    values (:id, :photoId, :appUserId)
                    on conflict (photo_id, app_user_id) do nothing
                    """,
            nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("photoId") UUID photoId,
            @Param("appUserId") UUID appUserId);
}
