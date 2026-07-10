package org.zipzip.zipzipserver.domain.chat.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zipzip.zipzipserver.domain.chat.code.ChatErrorCode;
import org.zipzip.zipzipserver.domain.chat.code.ChatSuccessCode;
import org.zipzip.zipzipserver.domain.chat.dto.request.CreateChatMessageRequest;
import org.zipzip.zipzipserver.domain.chat.dto.response.ChatAuthorResponse;
import org.zipzip.zipzipserver.domain.chat.dto.response.ChatMessageResponse;
import org.zipzip.zipzipserver.domain.chat.dto.response.ChatTimelineItemResponse;
import org.zipzip.zipzipserver.domain.chat.dto.response.ChatTimelineResponse;
import org.zipzip.zipzipserver.domain.chat.dto.response.ChatTimelineType;
import org.zipzip.zipzipserver.domain.chat.entity.SharedGroupChatMessage;
import org.zipzip.zipzipserver.domain.chat.repository.ChatTimelineItemProjection;
import org.zipzip.zipzipserver.domain.chat.repository.SharedGroupChatMessageRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.code.SharedGroupErrorCode;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupMembership;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupMembershipRepository;
import org.zipzip.zipzipserver.global.code.GlobalErrorCode;
import org.zipzip.zipzipserver.global.exception.BusinessException;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyResponseSupport;
import org.zipzip.zipzipserver.global.idempotency.IdempotencyService;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_CONTENT_LENGTH = 1000;
    private static final String CHAT_MESSAGE_CREATE_SCOPE_PREFIX = "CHAT_MESSAGE_CREATE:";
    private static final String CHAT_MESSAGE_CREATE_HTTP_METHOD = "POST";

    private final SharedGroupChatMessageRepository sharedGroupChatMessageRepository;
    private final SharedGroupMembershipRepository sharedGroupMembershipRepository;
    private final IdempotencyService idempotencyService;

    @Transactional(readOnly = true)
    public ChatTimelineResponse getTimeline(
            UUID appUserId, UUID sharedGroupId, String cursor, Integer requestedSize) {
        requireActiveMembership(sharedGroupId, appUserId);
        int pageSize = normalizePageSize(requestedSize);
        TimelineCursor timelineCursor = TimelineCursor.decode(cursor);
        List<ChatTimelineItemProjection> fetchedItems =
                sharedGroupChatMessageRepository.findTimelineItems(
                        sharedGroupId,
                        timelineCursor.createdAt(),
                        timelineCursor.typeOrder(),
                        timelineCursor.id(),
                        PageRequest.of(0, pageSize + 1));

        boolean hasNext = fetchedItems.size() > pageSize;
        List<ChatTimelineItemProjection> pageItems =
                hasNext ? fetchedItems.subList(0, pageSize) : fetchedItems;
        List<ChatTimelineItemResponse> items =
                pageItems.stream().map(item -> toTimelineItem(item, appUserId)).toList();
        String nextCursor = hasNext ? TimelineCursor.encode(pageItems.getLast()) : null;

        return new ChatTimelineResponse(items, nextCursor, hasNext);
    }

    @Transactional
    public CreateChatMessageResult createMessage(
            UUID appUserId,
            UUID sharedGroupId,
            String idempotencyKeyHeader,
            CreateChatMessageRequest request) {
        SharedGroupMembership membership = requireActiveMembership(sharedGroupId, appUserId);
        String content = normalizeContent(request.content());
        UUID idempotencyKey = IdempotencyResponseSupport.parseKey(idempotencyKeyHeader);
        String scope = CHAT_MESSAGE_CREATE_SCOPE_PREFIX + appUserId + ":" + sharedGroupId;
        String apiPath = "/api/v1/shared-groups/" + sharedGroupId + "/chat-messages";
        IdempotencyService.IdempotencyExecution<ChatMessageResponse> idempotencyExecution =
                idempotencyService.execute(
                        scope,
                        idempotencyKey,
                        CHAT_MESSAGE_CREATE_HTTP_METHOD,
                        apiPath,
                        new CreateChatMessageRequest(content),
                        ChatMessageResponse.class,
                        ChatSuccessCode.SHARED_GROUP_CHAT_MESSAGE_CREATED,
                        () -> {
                            SharedGroupChatMessage message =
                                    SharedGroupChatMessage.create(
                                            membership.getSharedGroup(),
                                            membership.getAppUser(),
                                            content);
                            SharedGroupChatMessage savedMessage =
                                    sharedGroupChatMessageRepository.saveAndFlush(message);
                            return ChatMessageResponse.from(savedMessage, appUserId);
                        });

        return new CreateChatMessageResult(
                idempotencyExecution.response(), idempotencyExecution.replayed());
    }

    private SharedGroupMembership requireActiveMembership(UUID sharedGroupId, UUID appUserId) {
        return sharedGroupMembershipRepository
                .findActiveBySharedGroupIdAndAppUserId(sharedGroupId, appUserId)
                .orElseThrow(
                        () -> new BusinessException(SharedGroupErrorCode.SHARED_GROUP_NOT_FOUND));
    }

    private int normalizePageSize(Integer requestedSize) {
        if (requestedSize == null) {
            return DEFAULT_PAGE_SIZE;
        }

        if (requestedSize < 1 || requestedSize > MAX_PAGE_SIZE) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }

        return requestedSize;
    }

    private String normalizeContent(String content) {
        if (content == null) {
            throw new BusinessException(ChatErrorCode.INVALID_CHAT_MESSAGE_CONTENT);
        }

        String normalizedContent = content.strip();
        if (normalizedContent.isBlank() || normalizedContent.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ChatErrorCode.INVALID_CHAT_MESSAGE_CONTENT);
        }

        return normalizedContent;
    }

    private ChatTimelineItemResponse toTimelineItem(
            ChatTimelineItemProjection item, UUID requestAppUserId) {
        ChatTimelineType type;
        try {
            type = ChatTimelineType.valueOf(item.getTimelineType());
        } catch (Exception exception) {
            throw new IllegalStateException("지원하지 않는 채팅 타임라인 유형입니다.", exception);
        }

        return new ChatTimelineItemResponse(
                type,
                item.getId(),
                item.getPhotoId(),
                item.getContent(),
                new ChatAuthorResponse(item.getAuthorId(), item.getAuthorDisplayName()),
                item.getAuthorId().equals(requestAppUserId),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }

    public record CreateChatMessageResult(ChatMessageResponse response, boolean replayed) {}

    private record TimelineCursor(Instant createdAt, Integer typeOrder, UUID id) {

        private static final String FIELD_SEPARATOR = "|";

        static TimelineCursor decode(String cursor) {
            if (cursor == null || cursor.isBlank()) {
                return new TimelineCursor(null, null, null);
            }

            try {
                String decodedCursor =
                        new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                String[] values = decodedCursor.split("\\|", -1);
                if (values.length != 3) {
                    throw new IllegalArgumentException("cursor 형식이 올바르지 않습니다.");
                }

                Instant createdAt = Instant.parse(values[0]);
                ChatTimelineType type = ChatTimelineType.valueOf(values[1]);
                UUID id = UUID.fromString(values[2]);
                return new TimelineCursor(createdAt, type.getOrder(), id);
            } catch (Exception exception) {
                throw new BusinessException(ChatErrorCode.INVALID_CURSOR);
            }
        }

        static String encode(ChatTimelineItemProjection item) {
            ChatTimelineType type = ChatTimelineType.valueOf(item.getTimelineType());
            String rawCursor =
                    item.getCreatedAt()
                            + FIELD_SEPARATOR
                            + type.name()
                            + FIELD_SEPARATOR
                            + item.getId();
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(rawCursor.getBytes(StandardCharsets.UTF_8));
        }
    }
}
