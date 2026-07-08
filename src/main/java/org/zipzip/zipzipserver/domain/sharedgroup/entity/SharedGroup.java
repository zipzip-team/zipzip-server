package org.zipzip.zipzipserver.domain.sharedgroup.entity;

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
@Table(name = "shared_group")
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SharedGroup extends BaseTimeEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_app_user_id", nullable = false)
    private AppUser createdByAppUser;

    @Column(nullable = false, length = 100)
    private String name;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invite_code", nullable = false, unique = true)
    private InviteCodeReservation inviteCodeReservation;

    private Instant deletedAt;

    public static SharedGroup create(
            AppUser createdByAppUser, String name, InviteCodeReservation inviteCodeReservation) {
        return SharedGroup.builder()
                .id(UUID.randomUUID())
                .createdByAppUser(createdByAppUser)
                .name(name)
                .inviteCodeReservation(inviteCodeReservation)
                .build();
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void delete(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
