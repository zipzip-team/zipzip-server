package org.zipzip.zipzipserver.domain.photo.entity;

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
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbum;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.global.jpa.BaseTimeEntity;

@Getter
@Entity
@Table(name = "photo")
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Photo extends BaseTimeEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shared_album_id", nullable = false)
    private SharedAlbum sharedAlbum;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_app_user_id", nullable = false)
    private AppUser uploadedByAppUser;

    @Column(nullable = false, unique = true, columnDefinition = "text")
    private String objectKey;

    @Column(nullable = false, length = 255)
    private String originalFileName;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private Long fileSize;

    private Instant takenAt;

    private Instant deletedAt;

    public static Photo create(
            SharedAlbum sharedAlbum,
            AppUser uploadedByAppUser,
            String objectKey,
            String originalFileName,
            String contentType,
            Long fileSize,
            Instant takenAt) {
        return Photo.builder()
                .id(UUID.randomUUID())
                .sharedAlbum(sharedAlbum)
                .uploadedByAppUser(uploadedByAppUser)
                .objectKey(objectKey)
                .originalFileName(originalFileName)
                .contentType(contentType)
                .fileSize(fileSize)
                .takenAt(takenAt)
                .build();
    }

    public void updateTakenAt(Instant takenAt) {
        this.takenAt = takenAt;
    }

    public void delete(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
