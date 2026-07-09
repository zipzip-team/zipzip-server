package org.zipzip.zipzipserver.domain.sharedgroup.repository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupMembership;

public interface SharedGroupMembershipRepository
        extends JpaRepository<SharedGroupMembership, UUID> {

    @Query(
            """
            select count(m) > 0
            from SharedGroupMembership m
            join m.sharedGroup g
            join m.appUser u
            where g.id = :sharedGroupId
              and u.id = :appUserId
              and g.deletedAt is null
              and u.deletedAt is null
            """)
    boolean existsActiveMembership(
            @Param("sharedGroupId") UUID sharedGroupId, @Param("appUserId") UUID appUserId);

    @Query(
            """
            select new org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupMemberRow(
                m.id,
                u.id,
                u.displayName,
                m.role,
                m.createdAt
            )
            from SharedGroupMembership m
            join m.sharedGroup g
            join m.appUser u
            where g.id = :sharedGroupId
              and g.deletedAt is null
              and u.deletedAt is null
            order by m.createdAt asc, m.id asc
            """)
    List<SharedGroupMemberRow> findActiveMembers(
            @Param("sharedGroupId") UUID sharedGroupId, Pageable pageable);

    @Query(
            """
            select new org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupMemberRow(
                m.id,
                u.id,
                u.displayName,
                m.role,
                m.createdAt
            )
            from SharedGroupMembership m
            join m.sharedGroup g
            join m.appUser u
            where g.id = :sharedGroupId
              and g.deletedAt is null
              and u.deletedAt is null
              and (
                    m.createdAt > :cursorJoinedAt
                    or (m.createdAt = :cursorJoinedAt and m.id > :cursorMembershipId)
                  )
            order by m.createdAt asc, m.id asc
            """)
    List<SharedGroupMemberRow> findActiveMembersAfter(
            @Param("sharedGroupId") UUID sharedGroupId,
            @Param("cursorJoinedAt") Instant cursorJoinedAt,
            @Param("cursorMembershipId") UUID cursorMembershipId,
            Pageable pageable);
}
