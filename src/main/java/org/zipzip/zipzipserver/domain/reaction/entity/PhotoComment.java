package org.zipzip.zipzipserver.domain.reaction.entity;

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
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.global.jpa.BaseTimeEntity;

@Getter
@Entity
@Table(name = "photo_comment")
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhotoComment extends BaseTimeEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "photo_id", nullable = false)
    private Photo photo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser appUser;

    @Column(nullable = false, length = 1000)
    private String content;

    private Instant deletedAt;

    public static PhotoComment create(Photo photo, AppUser appUser, String content) {
        return PhotoComment.builder()
                .id(UUID.randomUUID())
                .photo(photo)
                .appUser(appUser)
                .content(content)
                .build();
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void delete(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
