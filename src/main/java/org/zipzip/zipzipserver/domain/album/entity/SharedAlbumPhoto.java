package org.zipzip.zipzipserver.domain.album.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.global.jpa.BaseTimeEntity;

@Getter
@Entity
@Table(
        name = "shared_album_photo",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_shared_album_photo__shared_album_id_photo_id",
                    columnNames = {"shared_album_id", "photo_id"})
        })
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SharedAlbumPhoto extends BaseTimeEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shared_album_id", nullable = false)
    private SharedAlbum sharedAlbum;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "photo_id", nullable = false)
    private Photo photo;

    public static SharedAlbumPhoto create(SharedAlbum sharedAlbum, Photo photo) {
        return SharedAlbumPhoto.builder()
                .id(UUID.randomUUID())
                .sharedAlbum(sharedAlbum)
                .photo(photo)
                .build();
    }
}
