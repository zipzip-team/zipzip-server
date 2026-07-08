package org.zipzip.zipzipserver.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.global.jpa.BaseTimeEntity;

@Getter
@Entity
@Table(name = "refresh_token")
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseTimeEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser appUser;

    @Column(nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(nullable = false)
    private UUID tokenFamilyId;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_refresh_token_id", unique = true)
    private RefreshToken replacedByRefreshToken;

    public static RefreshToken create(
            AppUser appUser, String tokenHash, UUID tokenFamilyId, Instant expiresAt) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .appUser(appUser)
                .tokenHash(tokenHash)
                .tokenFamilyId(tokenFamilyId)
                .expiresAt(expiresAt)
                .build();
    }

    public void revoke(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public void replaceBy(RefreshToken replacedByRefreshToken) {
        this.replacedByRefreshToken = replacedByRefreshToken;
    }
}
