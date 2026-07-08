package org.zipzip.zipzipserver.domain.album.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.global.jpa.BaseTimeEntity;

@Getter
@Entity
@Table(name = "shared_album")
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SharedAlbum extends BaseTimeEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shared_group_id", nullable = false)
    private SharedGroup sharedGroup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_app_user_id", nullable = false)
    private AppUser createdByAppUser;

    @Column(nullable = false, length = 100)
    private String name;

    private Instant deletedAt;

    public static SharedAlbum create(
            SharedGroup sharedGroup, AppUser createdByAppUser, String name) {
        return SharedAlbum.builder()
                .id(UUID.randomUUID())
                .sharedGroup(sharedGroup)
                .createdByAppUser(createdByAppUser)
                .name(name)
                .build();
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void delete(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
