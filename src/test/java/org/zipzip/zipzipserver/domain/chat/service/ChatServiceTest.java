package org.zipzip.zipzipserver.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.zipzip.zipzipserver.domain.chat.code.ChatErrorCode;
import org.zipzip.zipzipserver.domain.chat.dto.request.CreateChatMessageRequest;
import org.zipzip.zipzipserver.domain.chat.dto.response.ChatMessageResponse;
import org.zipzip.zipzipserver.domain.chat.dto.response.ChatTimelineResponse;
import org.zipzip.zipzipserver.domain.chat.dto.response.ChatTimelineType;
import org.zipzip.zipzipserver.domain.chat.entity.SharedGroupChatMessage;
import org.zipzip.zipzipserver.domain.chat.repository.ChatTimelineItemProjection;
import org.zipzip.zipzipserver.domain.chat.repository.SharedGroupChatMessageRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.code.SharedGroupErrorCode;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.InviteCodeReservation;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupMembership;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupMembershipRepository;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.global.exception.BusinessException;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyService;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final UUID APP_USER_ID = UUID.fromString("018f0c3e-2c77-7d72-a37e-2f5666f25d32");
    private static final UUID SHARED_GROUP_ID =
            UUID.fromString("b8a5f612-25d7-4ec3-9d1d-59684de40664");
    private static final UUID MESSAGE_ID = UUID.fromString("f8f86e19-24ce-4f18-9b2a-35a594ac9a17");
    private static final UUID COMMENT_ID = UUID.fromString("93f1cdb0-8ca8-4d05-a9c7-2c44b18ed326");
    private static final UUID PHOTO_ID = UUID.fromString("385ff765-b20c-49a2-8e62-e1457784aa15");
    private static final UUID IDEMPOTENCY_KEY =
            UUID.fromString("54cf8d7e-a23e-4e76-90f7-603f122b1507");
    private static final Instant CREATED_AT = Instant.parse("2026-07-03T10:15:30Z");

    @Mock private SharedGroupChatMessageRepository sharedGroupChatMessageRepository;
    @Mock private SharedGroupMembershipRepository sharedGroupMembershipRepository;
    @Mock private IdempotencyService idempotencyService;

    @InjectMocks private ChatService chatService;

    @Test
    void 일반_메시지와_사진_댓글을_타임라인으로_변환하고_다음_cursor를_반환한다() {
        givenActiveMembership();
        ChatTimelineItemProjection message =
                timelineItem(
                        MESSAGE_ID,
                        "CHAT_MESSAGE",
                        null,
                        "이번 여행 사진 올려줘!",
                        APP_USER_ID,
                        "집집이",
                        CREATED_AT);
        ChatTimelineItemProjection comment =
                timelineItem(
                        COMMENT_ID,
                        "PHOTO_COMMENT",
                        PHOTO_ID,
                        "사진 너무 좋다!",
                        UUID.randomUUID(),
                        "다른 사용자",
                        CREATED_AT.minusSeconds(1));
        ChatTimelineItemProjection olderMessage = mock(ChatTimelineItemProjection.class);
        when(sharedGroupChatMessageRepository.findTimelineItems(
                        eq(SHARED_GROUP_ID),
                        nullable(Instant.class),
                        nullable(Integer.class),
                        nullable(UUID.class),
                        any(Pageable.class)))
                .thenReturn(List.of(message, comment, olderMessage));

        ChatTimelineResponse response =
                chatService.getTimeline(APP_USER_ID, SHARED_GROUP_ID, null, 2);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().getFirst().type()).isEqualTo(ChatTimelineType.CHAT_MESSAGE);
        assertThat(response.items().getFirst().photoId()).isNull();
        assertThat(response.items().getFirst().isAuthor()).isTrue();
        assertThat(response.items().get(1).type()).isEqualTo(ChatTimelineType.PHOTO_COMMENT);
        assertThat(response.items().get(1).photoId()).isEqualTo(PHOTO_ID);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isNotBlank();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(sharedGroupChatMessageRepository)
                .findTimelineItems(
                        eq(SHARED_GROUP_ID),
                        nullable(Instant.class),
                        nullable(Integer.class),
                        nullable(UUID.class),
                        pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    void 타임라인_cursor는_마지막_항목의_정렬_키를_다음_조회에_전달한다() {
        givenActiveMembership();
        ChatTimelineItemProjection message =
                timelineItem(
                        MESSAGE_ID,
                        "CHAT_MESSAGE",
                        null,
                        "이번 여행 사진 올려줘!",
                        APP_USER_ID,
                        "집집이",
                        CREATED_AT);
        ChatTimelineItemProjection comment = mock(ChatTimelineItemProjection.class);
        when(sharedGroupChatMessageRepository.findTimelineItems(
                        eq(SHARED_GROUP_ID),
                        nullable(Instant.class),
                        nullable(Integer.class),
                        nullable(UUID.class),
                        any(Pageable.class)))
                .thenReturn(List.of(message, comment));

        ChatTimelineResponse firstResponse =
                chatService.getTimeline(APP_USER_ID, SHARED_GROUP_ID, null, 1);

        when(sharedGroupChatMessageRepository.findTimelineItems(
                        eq(SHARED_GROUP_ID),
                        eq(CREATED_AT),
                        eq(ChatTimelineType.CHAT_MESSAGE.getOrder()),
                        eq(MESSAGE_ID),
                        any(Pageable.class)))
                .thenReturn(List.of());

        chatService.getTimeline(APP_USER_ID, SHARED_GROUP_ID, firstResponse.nextCursor(), 1);

        verify(sharedGroupChatMessageRepository)
                .findTimelineItems(
                        eq(SHARED_GROUP_ID),
                        eq(CREATED_AT),
                        eq(ChatTimelineType.CHAT_MESSAGE.getOrder()),
                        eq(MESSAGE_ID),
                        any(Pageable.class));
    }

    @Test
    void 잘못된_cursor는_예외가_발생한다() {
        givenActiveMembership();

        assertThatThrownBy(
                        () ->
                                chatService.getTimeline(
                                        APP_USER_ID, SHARED_GROUP_ID, "not-a-cursor", 30))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ChatErrorCode.INVALID_CURSOR));
    }

    @Test
    void 활성_멤버는_공백을_제거한_일반_메시지를_작성한다() {
        SharedGroupMembership membership = givenActiveMembership();
        when(idempotencyService.execute(
                        any(),
                        eq(IDEMPOTENCY_KEY),
                        eq("POST"),
                        any(),
                        eq(new CreateChatMessageRequest("사진 올려줘!")),
                        eq(ChatMessageResponse.class),
                        any(),
                        any()))
                .thenAnswer(
                        invocation -> {
                            Supplier<ChatMessageResponse> operation = invocation.getArgument(7);
                            return new IdempotencyService.IdempotencyExecution<>(
                                    operation.get(), false);
                        });
        when(sharedGroupChatMessageRepository.saveAndFlush(any(SharedGroupChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChatService.CreateChatMessageResult result =
                chatService.createMessage(
                        APP_USER_ID,
                        SHARED_GROUP_ID,
                        IDEMPOTENCY_KEY.toString(),
                        new CreateChatMessageRequest(" 사진 올려줘! "));

        assertThat(result.replayed()).isFalse();
        assertThat(result.response().content()).isEqualTo("사진 올려줘!");
        assertThat(result.response().author().userId()).isEqualTo(membership.getAppUser().getId());
        assertThat(result.response().isAuthor()).isTrue();

        ArgumentCaptor<SharedGroupChatMessage> messageCaptor =
                ArgumentCaptor.forClass(SharedGroupChatMessage.class);
        verify(sharedGroupChatMessageRepository).saveAndFlush(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getSharedGroup())
                .isEqualTo(membership.getSharedGroup());
        assertThat(messageCaptor.getValue().getAppUser()).isEqualTo(membership.getAppUser());
        assertThat(messageCaptor.getValue().getContent()).isEqualTo("사진 올려줘!");
        verify(idempotencyService)
                .execute(
                        any(),
                        eq(IDEMPOTENCY_KEY),
                        eq("POST"),
                        any(),
                        eq(new CreateChatMessageRequest("사진 올려줘!")),
                        eq(ChatMessageResponse.class),
                        any(),
                        any());
    }

    @Test
    void 같은_멱등성_키의_완료_응답은_메시지를_다시_저장하지_않는다() {
        givenActiveMembership();
        ChatMessageResponse replayResponse =
                new ChatMessageResponse(
                        MESSAGE_ID,
                        "사진 올려줘!",
                        new org.zipzip.zipzipserver.domain.chat.dto.response.ChatAuthorResponse(
                                APP_USER_ID, "집집이"),
                        true,
                        CREATED_AT,
                        CREATED_AT);
        when(idempotencyService.execute(
                        any(),
                        eq(IDEMPOTENCY_KEY),
                        eq("POST"),
                        any(),
                        any(CreateChatMessageRequest.class),
                        eq(ChatMessageResponse.class),
                        any(),
                        any()))
                .thenReturn(new IdempotencyService.IdempotencyExecution<>(replayResponse, true));

        ChatService.CreateChatMessageResult result =
                chatService.createMessage(
                        APP_USER_ID,
                        SHARED_GROUP_ID,
                        IDEMPOTENCY_KEY.toString(),
                        new CreateChatMessageRequest("사진 올려줘!"));

        assertThat(result.replayed()).isTrue();
        assertThat(result.response()).isEqualTo(replayResponse);
        verify(sharedGroupChatMessageRepository, never()).saveAndFlush(any());
    }

    @Test
    void 공백이거나_너무_긴_메시지는_작성할_수_없다() {
        givenActiveMembership();

        assertThatThrownBy(
                        () ->
                                chatService.createMessage(
                                        APP_USER_ID,
                                        SHARED_GROUP_ID,
                                        IDEMPOTENCY_KEY.toString(),
                                        new CreateChatMessageRequest(" ")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ChatErrorCode.INVALID_CHAT_MESSAGE_CONTENT));
        assertThatThrownBy(
                        () ->
                                chatService.createMessage(
                                        APP_USER_ID,
                                        SHARED_GROUP_ID,
                                        IDEMPOTENCY_KEY.toString(),
                                        new CreateChatMessageRequest("가".repeat(1001))))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ChatErrorCode.INVALID_CHAT_MESSAGE_CONTENT));
        verify(idempotencyService, never())
                .execute(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void 활성_멤버십이_없으면_채팅을_조회하거나_작성할_수_없다() {
        when(sharedGroupMembershipRepository.findActiveBySharedGroupIdAndAppUserId(
                        SHARED_GROUP_ID, APP_USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getTimeline(APP_USER_ID, SHARED_GROUP_ID, null, 30))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(SharedGroupErrorCode.SHARED_GROUP_NOT_FOUND));
        verify(sharedGroupChatMessageRepository, never())
                .findTimelineItems(any(), any(), any(), any(), any());
    }

    private SharedGroupMembership givenActiveMembership() {
        AppUser appUser = AppUser.create("apple-subject", "집집이");
        ReflectionTestUtils.setField(appUser, "id", APP_USER_ID);
        SharedGroup sharedGroup =
                SharedGroup.create(appUser, "여름 여행", InviteCodeReservation.create("INVITE1"));
        SharedGroupMembership membership =
                SharedGroupMembership.create(sharedGroup, appUser, SharedGroupRole.HOST);
        when(sharedGroupMembershipRepository.findActiveBySharedGroupIdAndAppUserId(
                        SHARED_GROUP_ID, APP_USER_ID))
                .thenReturn(Optional.of(membership));
        return membership;
    }

    private ChatTimelineItemProjection timelineItem(
            UUID id,
            String type,
            UUID photoId,
            String content,
            UUID authorId,
            String authorDisplayName,
            Instant createdAt) {
        ChatTimelineItemProjection item = mock(ChatTimelineItemProjection.class);
        when(item.getId()).thenReturn(id);
        when(item.getTimelineType()).thenReturn(type);
        when(item.getPhotoId()).thenReturn(photoId);
        when(item.getContent()).thenReturn(content);
        when(item.getAuthorId()).thenReturn(authorId);
        when(item.getAuthorDisplayName()).thenReturn(authorDisplayName);
        when(item.getCreatedAt()).thenReturn(createdAt);
        when(item.getUpdatedAt()).thenReturn(createdAt);
        return item;
    }
}
