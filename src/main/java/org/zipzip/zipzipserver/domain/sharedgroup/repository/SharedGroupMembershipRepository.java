package org.zipzip.zipzipserver.domain.sharedgroup.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupMembership;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;

public interface SharedGroupMembershipRepository
        extends JpaRepository<SharedGroupMembership, UUID> {

    void deleteByAppUserIdAndRole(UUID appUserId, SharedGroupRole role);
}
