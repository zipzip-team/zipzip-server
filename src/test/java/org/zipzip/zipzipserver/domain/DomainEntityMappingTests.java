package org.zipzip.zipzipserver.domain;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbum;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbumPhoto;
import org.zipzip.zipzipserver.domain.chat.entity.SharedGroupChatMessage;
import org.zipzip.zipzipserver.domain.device.entity.Device;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.photo.entity.PhotoThumbnailStatus;
import org.zipzip.zipzipserver.domain.photo.entity.PhotoUploadReservation;
import org.zipzip.zipzipserver.domain.reaction.entity.PhotoComment;
import org.zipzip.zipzipserver.domain.reaction.entity.PhotoLike;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.InviteCodeReservation;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupMembership;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.global.jpa.BaseTimeEntity;

class DomainEntityMappingTests {

    @Test
    void sharedGroupMembershipDeclaresGroupUserUniqueConstraint() {
        assertUniqueConstraint(
                SharedGroupMembership.class,
                "uk_shared_group_membership__group_user",
                "shared_group_id",
                "app_user_id");
    }

    @Test
    void photoLikeDeclaresPhotoUserUniqueConstraint() {
        assertUniqueConstraint(
                PhotoLike.class, "uk_photo_like__photo_id_app_user_id", "photo_id", "app_user_id");
    }

    @Test
    void sharedAlbumPhotoDeclaresAlbumPhotoUniqueConstraint() {
        assertUniqueConstraint(
                SharedAlbumPhoto.class,
                "uk_shared_album_photo__shared_album_id_photo_id",
                "shared_album_id",
                "photo_id");
    }

    @Test
    void baseTimeEntityUsesInstantAuditFields() throws NoSuchFieldException {
        Field createdAt = BaseTimeEntity.class.getDeclaredField("createdAt");
        Field updatedAt = BaseTimeEntity.class.getDeclaredField("updatedAt");

        assertThat(createdAt.getType()).isEqualTo(Instant.class);
        assertThat(createdAt.getAnnotation(CreatedDate.class)).isNotNull();
        assertThat(updatedAt.getType()).isEqualTo(Instant.class);
        assertThat(updatedAt.getAnnotation(LastModifiedDate.class)).isNotNull();
    }

    @Test
    void softDeleteDomainMethodsStoreDeletedAtInstant() {
        Instant deletedAt = Instant.parse("2026-07-08T04:00:00Z");
        AppUser user = AppUser.create("apple-subject", "사용자");
        InviteCodeReservation inviteCodeReservation = InviteCodeReservation.create("INVITE1");
        SharedGroup sharedGroup = SharedGroup.create(user, "공유 그룹", inviteCodeReservation);
        SharedAlbum sharedAlbum = SharedAlbum.create(sharedGroup, user, "앨범");
        Device device = Device.create(user, "iPhone 15");
        Photo photo = Photo.create(user, device, "photos/385ff765/original.jpg", null, 1080, 1920);
        SharedAlbumPhoto sharedAlbumPhoto = SharedAlbumPhoto.create(sharedAlbum, photo);

        user.withdraw(deletedAt);
        sharedGroup.delete(deletedAt);
        sharedAlbum.delete(deletedAt);
        photo.delete(deletedAt);
        device.delete(deletedAt);

        assertThat(user.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(user.getDisplayName()).isEqualTo(AppUser.WITHDRAWN_DISPLAY_NAME);
        assertThat(sharedGroup.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(sharedAlbum.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(photo.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(device.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(sharedAlbumPhoto.getSharedAlbum()).isEqualTo(sharedAlbum);
        assertThat(sharedAlbumPhoto.getPhoto()).isEqualTo(photo);
    }

    @Test
    void hardDeletedEntitiesHaveNoDeletedAtField() {
        assertThat(hasDeclaredField(PhotoComment.class, "deletedAt")).isFalse();
        assertThat(hasDeclaredField(SharedGroupChatMessage.class, "deletedAt")).isFalse();
        assertThat(hasDeclaredField(PhotoLike.class, "deletedAt")).isFalse();
    }

    @Test
    void photoThumbnailStatusTransitions() {
        AppUser user = AppUser.create("apple-subject", "사용자");
        Photo photo = Photo.create(user, null, "photos/385ff765/original.jpg", null, null, null);

        assertThat(photo.getThumbnailStatus()).isEqualTo(PhotoThumbnailStatus.PENDING);
        assertThat(photo.getThumbnailObjectKey()).isNull();

        photo.markThumbnailReady("photos/385ff765/thumbnail.jpg");

        assertThat(photo.getThumbnailStatus()).isEqualTo(PhotoThumbnailStatus.READY);
        assertThat(photo.getThumbnailObjectKey()).isEqualTo("photos/385ff765/thumbnail.jpg");

        photo.markThumbnailFailed();

        assertThat(photo.getThumbnailStatus()).isEqualTo(PhotoThumbnailStatus.FAILED);
    }

    @Test
    void photoUploadReservationIsUsableOnlyByMatchingUserAndAlbumBeforeExpiry() {
        AppUser owner = AppUser.create("apple-subject-owner", "업로더");
        AppUser stranger = AppUser.create("apple-subject-stranger", "다른 사용자");
        SharedGroup sharedGroup =
                SharedGroup.create(owner, "공유 그룹", InviteCodeReservation.create("INVITE1"));
        SharedAlbum sharedAlbum = SharedAlbum.create(sharedGroup, owner, "앨범");
        SharedAlbum otherAlbum = SharedAlbum.create(sharedGroup, owner, "다른 앨범");
        Instant expiresAt = Instant.parse("2026-07-08T04:00:00Z");
        PhotoUploadReservation reservation =
                PhotoUploadReservation.create(
                        "photos/385ff765/original.jpg", sharedAlbum, owner, expiresAt);

        assertThat(reservation.isUsableBy(sharedAlbum, owner, expiresAt.minusSeconds(1))).isTrue();
        assertThat(reservation.isUsableBy(sharedAlbum, stranger, expiresAt.minusSeconds(1)))
                .isFalse();
        assertThat(reservation.isUsableBy(otherAlbum, owner, expiresAt.minusSeconds(1))).isFalse();
        assertThat(reservation.isUsableBy(sharedAlbum, owner, expiresAt.plusSeconds(1))).isFalse();
    }

    @Test
    void sharedGroupMembershipFactoryStoresRole() {
        AppUser user = AppUser.create("apple-subject", "사용자");
        SharedGroup sharedGroup =
                SharedGroup.create(user, "공유 그룹", InviteCodeReservation.create("INVITE1"));
        SharedGroupMembership membership =
                SharedGroupMembership.create(sharedGroup, user, SharedGroupRole.HOST);

        assertThat(membership.getSharedGroup()).isEqualTo(sharedGroup);
        assertThat(membership.getAppUser()).isEqualTo(user);
        assertThat(membership.getRole()).isEqualTo(SharedGroupRole.HOST);
    }

    private boolean hasDeclaredField(Class<?> entityClass, String fieldName) {
        try {
            entityClass.getDeclaredField(fieldName);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    private void assertUniqueConstraint(
            Class<?> entityClass, String constraintName, String... columnNames) {
        Table table = entityClass.getAnnotation(Table.class);

        assertThat(table).isNotNull();
        assertThat(table.uniqueConstraints())
                .anySatisfy(
                        uniqueConstraint -> {
                            assertThat(uniqueConstraint.name()).isEqualTo(constraintName);
                            assertThat(uniqueConstraint.columnNames()).containsExactly(columnNames);
                        });
    }
}
