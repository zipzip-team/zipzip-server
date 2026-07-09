package org.zipzip.zipzipserver.domain.auth.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.zipzip.zipzipserver.domain.auth.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select refreshToken from RefreshToken refreshToken where refreshToken.tokenHash ="
                    + " :tokenHash")
    Optional<RefreshToken> findWithLockByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select refreshToken
            from RefreshToken refreshToken
            where refreshToken.appUser.id = :appUserId
              and refreshToken.tokenFamilyId = :tokenFamilyId
            """)
    List<RefreshToken> findWithLockByAppUserIdAndTokenFamilyId(
            @Param("appUserId") UUID appUserId, @Param("tokenFamilyId") UUID tokenFamilyId);
}
