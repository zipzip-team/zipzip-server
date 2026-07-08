package org.zipzip.zipzipserver.domain.sharedgroup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(
        name = "shared_group_membership",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_shared_group_membership__group_user",
                    columnNames = {"shared_group_id", "app_user_id"})
        })
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SharedGroupMembership extends BaseTimeEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shared_group_id", nullable = false)
    private SharedGroup sharedGroup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser appUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SharedGroupRole role;

    public static SharedGroupMembership create(
            SharedGroup sharedGroup, AppUser appUser, SharedGroupRole role) {
        return SharedGroupMembership.builder()
                .id(UUID.randomUUID())
                .sharedGroup(sharedGroup)
                .appUser(appUser)
                .role(role)
                .build();
    }
}
