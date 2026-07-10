package org.zipzip.zipzipserver.domain.reaction.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.zipzip.zipzipserver.domain.reaction.entity.PhotoLike;

public interface PhotoLikeRepository extends JpaRepository<PhotoLike, UUID> {

    void deleteByAppUserId(UUID appUserId);
}
