package org.zipzip.zipzipserver.domain.photo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbum;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;

@Getter
@Entity
@Table(name = "photo_upload_reservation")
@EntityListeners(AuditingEntityListener.class)
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhotoUploadReservation {

    @Id
    @Column(nullable = false, updatable = false, length = 500)
    private String objectKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shared_album_id", nullable = false)
    private SharedAlbum sharedAlbum;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_app_user_id", nullable = false)
    private AppUser requestedByAppUser;

    @Column(nullable = false)
    private Instant expiresAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static PhotoUploadReservation create(
            String objectKey,
            SharedAlbum sharedAlbum,
            AppUser requestedByAppUser,
            Instant expiresAt) {
        return PhotoUploadReservation.builder()
                .objectKey(objectKey)
                .sharedAlbum(sharedAlbum)
                .requestedByAppUser(requestedByAppUser)
                .expiresAt(expiresAt)
                .build();
    }

    public boolean isUsableBy(SharedAlbum sharedAlbum, AppUser requestedByAppUser, Instant now) {
        return this.sharedAlbum.getId().equals(sharedAlbum.getId())
                && this.requestedByAppUser.getId().equals(requestedByAppUser.getId())
                && expiresAt.isAfter(now);
    }
}
