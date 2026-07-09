package org.zipzip.zipzipserver.domain.sharedgroup.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.InviteCodeReservation;

public interface InviteCodeReservationRepository
        extends JpaRepository<InviteCodeReservation, String> {

    @Modifying(flushAutomatically = true)
    @Query(
            value =
                    """
                    insert into invite_code_reservation (invite_code)
                    values (:inviteCode)
                    on conflict (invite_code) do nothing
                    """,
            nativeQuery = true)
    int insertIfAbsent(@Param("inviteCode") String inviteCode);
}
