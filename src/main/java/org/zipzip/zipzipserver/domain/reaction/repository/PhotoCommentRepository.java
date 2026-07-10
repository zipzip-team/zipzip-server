package org.zipzip.zipzipserver.domain.reaction.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.zipzip.zipzipserver.domain.reaction.entity.PhotoComment;

public interface PhotoCommentRepository extends JpaRepository<PhotoComment, UUID> {

    void deleteByPhotoId(UUID photoId);
}
