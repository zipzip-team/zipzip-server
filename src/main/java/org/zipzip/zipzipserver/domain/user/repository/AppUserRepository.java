package org.zipzip.zipzipserver.domain.user.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByAppleSubject(String appleSubject);

    Optional<AppUser> findByIdAndDeletedAtIsNull(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select appUser from AppUser appUser where appUser.id = :id and appUser.deletedAt is"
                    + " null")
    Optional<AppUser> findWithLockByIdAndDeletedAtIsNull(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    """
                    insert into app_user (id, apple_subject, display_name)
                    values (:id, :appleSubject, :displayName)
                    on conflict (apple_subject) do nothing
                    """,
            nativeQuery = true)
    int insertIfAppleSubjectAbsent(
            @Param("id") UUID id,
            @Param("appleSubject") String appleSubject,
            @Param("displayName") String displayName);
}
