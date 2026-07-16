package org.zipzip.zipzipserver.domain.reaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbum;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbumPhoto;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumPhotoRepository;
import org.zipzip.zipzipserver.domain.photo.code.PhotoErrorCode;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.photo.entity.PhotoThumbnailStatus;
import org.zipzip.zipzipserver.domain.photo.repository.PhotoRepository;
import org.zipzip.zipzipserver.domain.photo.service.PhotoAccessGuard;
import org.zipzip.zipzipserver.domain.reaction.code.ReactionErrorCode;
import org.zipzip.zipzipserver.domain.reaction.code.ReactionSuccessCode;
import org.zipzip.zipzipserver.domain.reaction.dto.request.PhotoCommentCreateRequest;
import org.zipzip.zipzipserver.domain.reaction.dto.response.PhotoCommentListResponse;
import org.zipzip.zipzipserver.domain.reaction.dto.response.PhotoCommentResponse;
import org.zipzip.zipzipserver.domain.reaction.dto.response.PhotoDetailResponse;
import org.zipzip.zipzipserver.domain.reaction.dto.response.PhotoLikeResponse;
import org.zipzip.zipzipserver.domain.reaction.entity.PhotoComment;
import org.zipzip.zipzipserver.domain.reaction.repository.PhotoCommentRepository;
import org.zipzip.zipzipserver.domain.reaction.repository.PhotoLikeRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.InviteCodeReservation;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.storage.ObjectStorageService;
import org.zipzip.zipzipserver.domain.storage.PresignedDownload;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.global.exception.BusinessException;
import org.zipzip.zipzipserver.global.idempotency.ApiIdempotencyRecord;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyService;

@ExtendWith(MockitoExtension.class)
class ReactionServiceTest {

    @Mock private PhotoRepository photoRepository;
    @Mock private PhotoAccessGuard photoAccessGuard;
    @Mock private SharedAlbumPhotoRepository sharedAlbumPhotoRepository;
    @Mock private PhotoLikeRepository photoLikeRepository;
    @Mock private PhotoCommentRepository photoCommentRepository;
    @Mock private ObjectStorageService objectStorageService;
    @Mock private IdempotencyService idempotencyService;
    @Mock private EntityManager entityManager;

    @InjectMocks private ReactionService reactionService;

    private AppUser author;
    private AppUser requester;
    private SharedGroup sharedGroup;
    private SharedAlbum sharedAlbum;
    private Photo photo;

    @BeforeEach
    void setUp() {
        author = AppUser.create("author-" + UUID.randomUUID(), "작성자");
        requester = AppUser.create("requester-" + UUID.randomUUID(), "요청자");
        sharedGroup =
                SharedGroup.create(
                        author, "그룹", InviteCodeReservation.create("CODE" + UUID.randomUUID()));
        sharedAlbum = SharedAlbum.create(sharedGroup, author, "앨범");
        photo = Photo.create(author, "iPhone 15", "photos/original.jpg", null, 4032, 3024);
    }

    @Test
    void 사진_상세를_조회하면_presigned_url과_반응_요약을_반환한다() {
        givenAccessiblePhoto();
        when(sharedAlbumPhotoRepository.findByPhotoId(photo.getId()))
                .thenReturn(List.of(SharedAlbumPhoto.create(sharedAlbum, photo)));
        when(objectStorageService.issueDownloadUrl(eq("photos/original.jpg"), any()))
                .thenReturn(
                        new PresignedDownload(
                                "https://storage.example/original",
                                Instant.parse("2026-07-10T01:10:00Z")));
        when(photoLikeRepository.countByPhotoId(photo.getId())).thenReturn(3L);
        when(photoCommentRepository.countByPhotoId(photo.getId())).thenReturn(2L);
        when(photoLikeRepository.existsByPhotoIdAndAppUserId(photo.getId(), requester.getId()))
                .thenReturn(true);

        PhotoDetailResponse response =
                reactionService.getPhotoDetail(photo.getId(), requester.getId());

        assertThat(response.id()).isEqualTo(photo.getId());
        assertThat(response.sharedGroupId()).isEqualTo(sharedGroup.getId());
        assertThat(response.sharedAlbumIds()).containsExactly(sharedAlbum.getId());
        assertThat(response.originalUrl()).isEqualTo("https://storage.example/original");
        assertThat(response.likeCount()).isEqualTo(3);
        assertThat(response.commentCount()).isEqualTo(2);
        assertThat(response.isLikedByMe()).isTrue();
    }

    @Test
    void 공백_키를_가진_기존_READY_썸네일에는_URL을_발급하지_않는다() {
        ReflectionTestUtils.setField(photo, "thumbnailStatus", PhotoThumbnailStatus.READY);
        ReflectionTestUtils.setField(photo, "thumbnailObjectKey", " \t");
        givenAccessiblePhoto();
        when(sharedAlbumPhotoRepository.findByPhotoId(photo.getId()))
                .thenReturn(List.of(SharedAlbumPhoto.create(sharedAlbum, photo)));
        when(objectStorageService.issueDownloadUrl(eq("photos/original.jpg"), any()))
                .thenReturn(
                        new PresignedDownload(
                                "https://storage.example/original",
                                Instant.parse("2026-07-10T01:10:00Z")));

        PhotoDetailResponse response =
                reactionService.getPhotoDetail(photo.getId(), requester.getId());

        assertThat(response.thumbnailUrl()).isNull();
        verify(objectStorageService, org.mockito.Mockito.never())
                .issueDownloadUrl(eq(" \t"), any());
    }

    @Test
    void 좋아요_설정은_insert_on_conflict_방식으로_멱등_처리한다() {
        givenAccessiblePhoto();
        when(photoLikeRepository.countByPhotoId(photo.getId())).thenReturn(4L);

        PhotoLikeResponse response = reactionService.likePhoto(photo.getId(), requester.getId());

        assertThat(response.isLikedByMe()).isTrue();
        assertThat(response.likeCount()).isEqualTo(4L);
        verify(photoLikeRepository)
                .insertIfAbsent(any(UUID.class), eq(photo.getId()), eq(requester.getId()));
    }

    @Test
    void 좋아요_취소는_좋아요가_없어도_현재_상태를_반환한다() {
        givenAccessiblePhoto();
        when(photoLikeRepository.countByPhotoId(photo.getId())).thenReturn(1L);

        PhotoLikeResponse response = reactionService.unlikePhoto(photo.getId(), requester.getId());

        assertThat(response.isLikedByMe()).isFalse();
        assertThat(response.likeCount()).isEqualTo(1L);
        verify(photoLikeRepository).deleteByPhotoIdAndAppUserId(photo.getId(), requester.getId());
    }

    @Test
    void 댓글_목록은_작성일시_오름차순_cursor를_반환한다() {
        givenAccessiblePhoto();
        PhotoComment first = aComment("첫 댓글", Instant.parse("2026-07-10T00:00:00Z"));
        PhotoComment second = aComment("둘째 댓글", Instant.parse("2026-07-10T00:00:01Z"));
        when(photoCommentRepository.findPageByPhotoId(
                        eq(photo.getId()), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(first, second));

        PhotoCommentListResponse response =
                reactionService.listComments(photo.getId(), requester.getId(), null, 1);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().content()).isEqualTo("첫 댓글");
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isNotBlank();
    }

    @Test
    void 댓글_작성은_본문을_trim하고_멱등성_응답을_저장한다() {
        givenAccessiblePhoto();
        when(entityManager.getReference(AppUser.class, requester.getId())).thenReturn(requester);
        ApiIdempotencyRecord record =
                ApiIdempotencyRecord.processing(
                        "PHOTO_COMMENT_CREATE:" + requester.getId(),
                        UUID.randomUUID(),
                        "POST",
                        "/api/v1/photos/" + photo.getId() + "/comments",
                        "a".repeat(64),
                        Instant.now().plusSeconds(600));
        when(idempotencyService.parseIdempotencyKey(anyString())).thenReturn(UUID.randomUUID());
        when(idempotencyService.hashCanonicalRequest(any())).thenReturn("a".repeat(64));
        when(idempotencyService.start(
                        anyString(),
                        any(),
                        eq("POST"),
                        anyString(),
                        anyString(),
                        eq(PhotoCommentResponse.class)))
                .thenReturn(new IdempotencyService.IdempotencyStart<>(record, null, false));
        when(photoCommentRepository.save(any(PhotoComment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReactionService.PhotoCommentCreateResult result =
                reactionService.createComment(
                        photo.getId(),
                        requester.getId(),
                        UUID.randomUUID().toString(),
                        new PhotoCommentCreateRequest("  사진 너무 좋다!  "));

        assertThat(result.replayed()).isFalse();
        assertThat(result.response().content()).isEqualTo("사진 너무 좋다!");
        ArgumentCaptor<PhotoComment> commentCaptor = ArgumentCaptor.forClass(PhotoComment.class);
        verify(photoCommentRepository).save(commentCaptor.capture());
        assertThat(commentCaptor.getValue().getContent()).isEqualTo("사진 너무 좋다!");
        verify(idempotencyService)
                .complete(record, ReactionSuccessCode.PHOTO_COMMENT_CREATED, result.response());
    }

    @Test
    void 댓글_본문이_공백이면_도메인_오류를_반환한다() {
        givenAccessiblePhoto();

        assertThatThrownBy(
                        () ->
                                reactionService.createComment(
                                        photo.getId(),
                                        requester.getId(),
                                        UUID.randomUUID().toString(),
                                        new PhotoCommentCreateRequest("   ")))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(
                                                ReactionErrorCode.INVALID_PHOTO_COMMENT_CONTENT));
    }

    @Test
    void 유효하지_않은_댓글_cursor는_오류를_반환한다() {
        givenAccessiblePhoto();

        assertThatThrownBy(
                        () ->
                                reactionService.listComments(
                                        photo.getId(), requester.getId(), "invalid", 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        exception ->
                                assertThat(((BusinessException) exception).getErrorCode())
                                        .isEqualTo(PhotoErrorCode.INVALID_CURSOR));
    }

    private void givenAccessiblePhoto() {
        when(photoRepository.findById(photo.getId())).thenReturn(Optional.of(photo));
    }

    private PhotoComment aComment(String content, Instant createdAt) {
        PhotoComment comment = PhotoComment.create(photo, author, content);
        ReflectionTestUtils.setField(comment, "createdAt", createdAt);
        ReflectionTestUtils.setField(comment, "updatedAt", createdAt);
        return comment;
    }
}
