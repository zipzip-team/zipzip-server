package org.zipzip.zipzipserver.domain.sharedgroup.repository;

import java.time.Instant;
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

    @Query(
            """
            select sharedGroup
            from SharedGroup sharedGroup
            join fetch sharedGroup.inviteCodeReservation
            join fetch sharedGroup.createdByAppUser
            where sharedGroup.inviteCodeReservation.inviteCode = :inviteCode
              and sharedGroup.deletedAt is null
            """)
    Optional<SharedGroup> findActiveWithCreatorByInviteCode(@Param("inviteCode") String inviteCode);

    List<SharedGroup> findByCreatedByAppUserIdAndDeletedAtIsNull(UUID appUserId);

    @Query(
            value =
                    """
                    select id
                    from shared_group
                    where deleted_at <= :purgeBefore
                    order by deleted_at asc, id asc
                    limit :limit
                    """,
            nativeQuery = true)
    List<UUID> findPurgeCandidateIds(
            @Param("purgeBefore") Instant purgeBefore, @Param("limit") int limit);

    @Query(
            value =
                    """
                    select shared_group.*
                    from shared_group
                    where id = :sharedGroupId
                      and deleted_at <= :purgeBefore
                    for update skip locked
                    """,
            nativeQuery = true)
    Optional<SharedGroup> lockPurgeCandidateById(
            @Param("sharedGroupId") UUID sharedGroupId, @Param("purgeBefore") Instant purgeBefore);
}
