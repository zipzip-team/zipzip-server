package org.zipzip.zipzipserver.domain.sharedgroup.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;

public interface SharedGroupRepository extends JpaRepository<SharedGroup, UUID> {

    List<SharedGroup> findByCreatedByAppUserIdAndDeletedAtIsNull(UUID appUserId);
}
