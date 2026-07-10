package org.zipzip.zipzipserver.domain.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbum;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbumPhoto;
import org.zipzip.zipzipserver.domain.chat.entity.SharedGroupChatMessage;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.reaction.entity.PhotoComment;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.InviteCodeReservation;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupMembership;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.global.jpa.JpaAuditingConfig;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(JpaAuditingConfig.class)
class SharedGroupChatMessageRepositoryTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired private SharedGroupChatMessageRepository sharedGroupChatMessageRepository;
    @Autowired private TestEntityManager entityManager;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
                .cleanDisabled(false)
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .clean();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void 일반_메시지와_사진_댓글을_병합하고_여러_앨범에_속한_사진의_댓글을_한번만_반환한다() {
        TimelineFixture fixture = persistTimelineFixture();

        List<ChatTimelineItemProjection> items =
                sharedGroupChatMessageRepository.findTimelineItems(
                        fixture.sharedGroup().getId(), null, null, null, PageRequest.of(0, 10));

        assertThat(items).hasSize(2);
        assertThat(items)
                .extracting(ChatTimelineItemProjection::getTimelineType)
                .containsExactlyInAnyOrder("CHAT_MESSAGE", "PHOTO_COMMENT");
        assertThat(items)
                .filteredOn(item -> item.getTimelineType().equals("PHOTO_COMMENT"))
                .singleElement()
                .satisfies(
                        comment -> {
                            assertThat(comment.getId()).isEqualTo(fixture.photoComment().getId());
                            assertThat(comment.getPhotoId()).isEqualTo(fixture.photo().getId());
                            assertThat(comment.getAuthorId()).isEqualTo(fixture.appUser().getId());
                            assertThat(comment.getAuthorDisplayName()).isEqualTo("집집이");
                        });
    }

    private TimelineFixture persistTimelineFixture() {
        AppUser appUser = AppUser.create("apple-subject", "집집이");
        entityManager.persist(appUser);

        InviteCodeReservation inviteCodeReservation = InviteCodeReservation.create("INVITE1");
        entityManager.persist(inviteCodeReservation);
        SharedGroup sharedGroup = SharedGroup.create(appUser, "여름 여행", inviteCodeReservation);
        entityManager.persist(sharedGroup);
        entityManager.persist(
                SharedGroupMembership.create(sharedGroup, appUser, SharedGroupRole.HOST));

        SharedAlbum firstAlbum = SharedAlbum.create(sharedGroup, appUser, "첫 번째 앨범");
        SharedAlbum secondAlbum = SharedAlbum.create(sharedGroup, appUser, "두 번째 앨범");
        entityManager.persist(firstAlbum);
        entityManager.persist(secondAlbum);

        Photo photo =
                Photo.create(
                        appUser,
                        "iPhone 15",
                        "photos/" + java.util.UUID.randomUUID() + "/original.jpg",
                        null,
                        1080,
                        1920);
        entityManager.persist(photo);
        entityManager.persist(SharedAlbumPhoto.create(firstAlbum, photo));
        entityManager.persist(SharedAlbumPhoto.create(secondAlbum, photo));

        SharedGroupChatMessage message =
                SharedGroupChatMessage.create(sharedGroup, appUser, "이번 여행 사진 올려줘!");
        PhotoComment photoComment = PhotoComment.create(photo, appUser, "사진 너무 좋다!");
        entityManager.persist(message);
        entityManager.persist(photoComment);
        entityManager.flush();
        entityManager.clear();

        return new TimelineFixture(appUser, sharedGroup, photo, photoComment);
    }

    private record TimelineFixture(
            AppUser appUser, SharedGroup sharedGroup, Photo photo, PhotoComment photoComment) {}
}
