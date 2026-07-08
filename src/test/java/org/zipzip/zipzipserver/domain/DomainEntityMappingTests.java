package org.zipzip.zipzipserver.domain;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbum;
import org.zipzip.zipzipserver.domain.chat.entity.SharedGroupChatMessage;
import org.zipzip.zipzipserver.domain.device.entity.Device;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
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
        Photo photo =
                Photo.create(
                        sharedAlbum,
                        user,
                        "photos/photo.jpg",
                        "photo.jpg",
                        "image/jpeg",
                        1024L,
                        null);
        Device device = Device.create(user, "iPhone 15");
        SharedGroupChatMessage chatMessage =
                SharedGroupChatMessage.create(sharedGroup, user, "안녕하세요");
        PhotoComment photoComment = PhotoComment.create(photo, user, "좋은 사진이에요");

        user.withdraw(deletedAt);
        sharedGroup.delete(deletedAt);
        sharedAlbum.delete(deletedAt);
        photo.delete(deletedAt);
        device.delete(deletedAt);
        chatMessage.delete(deletedAt);
        photoComment.delete(deletedAt);

        assertThat(user.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(user.getDisplayName()).isEqualTo(AppUser.WITHDRAWN_DISPLAY_NAME);
        assertThat(sharedGroup.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(sharedAlbum.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(photo.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(device.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(chatMessage.getDeletedAt()).isEqualTo(deletedAt);
        assertThat(photoComment.getDeletedAt()).isEqualTo(deletedAt);
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
