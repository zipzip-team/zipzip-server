package org.zipzip.zipzipserver.domain.sharedgroup.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;

public interface SharedGroupRepository extends JpaRepository<SharedGroup, UUID> {

    @Query(
            """
            select sharedGroup
            from SharedGroup sharedGroup
            join fetch sharedGroup.inviteCodeReservation
            where sharedGroup.id = :sharedGroupId
              and sharedGroup.deletedAt is null
            """)
    Optional<SharedGroup> findActiveWithInviteCodeById(@Param("sharedGroupId") UUID sharedGroupId);

    @Query(
            """
            select sharedGroup
            from SharedGroup sharedGroup
            join fetch sharedGroup.inviteCodeReservation
            where sharedGroup.inviteCodeReservation.inviteCode = :inviteCode
              and sharedGroup.deletedAt is null
            """)
    Optional<SharedGroup> findActiveByInviteCode(@Param("inviteCode") String inviteCode);

    List<SharedGroup> findByCreatedByAppUserIdAndDeletedAtIsNull(UUID appUserId);
}
