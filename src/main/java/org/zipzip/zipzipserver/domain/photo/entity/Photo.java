package org.zipzip.zipzipserver.domain.photo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.zipzip.zipzipserver.domain.device.entity.Device;
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
    @JoinColumn(name = "uploaded_by_app_user_id", nullable = false)
    private AppUser uploadedByAppUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Column(nullable = false, unique = true, length = 500)
    private String originalObjectKey;

    @Column(length = 500)
    private String thumbnailObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PhotoThumbnailStatus thumbnailStatus;

    private Instant takenAt;

    private Double latitude;

    private Double longitude;

    @Column(length = 200)
    private String locationName;

    @Column(name = "is_inferred", nullable = false)
    private boolean inferred;

    private Integer width;

    private Integer height;

    private Instant deletedAt;

    public static Photo create(
            AppUser uploadedByAppUser,
            Device device,
            String originalObjectKey,
            Instant takenAt,
            Integer width,
            Integer height) {
        return Photo.builder()
                .id(UUID.randomUUID())
                .uploadedByAppUser(uploadedByAppUser)
                .device(device)
                .originalObjectKey(originalObjectKey)
                .thumbnailStatus(PhotoThumbnailStatus.PENDING)
                .takenAt(takenAt)
                .width(width)
                .height(height)
                .inferred(false)
                .build();
    }

    public void updateTakenAt(Instant takenAt) {
        this.takenAt = takenAt;
    }

    public void applyLocation(
            Double latitude, Double longitude, String locationName, boolean inferred) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationName = locationName;
        this.inferred = inferred;
    }

    public void markThumbnailReady(String thumbnailObjectKey) {
        this.thumbnailObjectKey = thumbnailObjectKey;
        this.thumbnailStatus = PhotoThumbnailStatus.READY;
    }

    public void markThumbnailFailed() {
        this.thumbnailStatus = PhotoThumbnailStatus.FAILED;
    }

    public void delete(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
