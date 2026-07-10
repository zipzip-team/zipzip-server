package org.zipzip.zipzipserver.domain.sharedgroup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.zipzip.zipzipserver.domain.sharedgroup.code.SharedGroupErrorCode;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupMemberListResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupMemberRow;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupMembershipRepository;
import org.zipzip.zipzipserver.global.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class SharedGroupMemberServiceTest {

    private static final UUID SHARED_GROUP_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000000");
    private static final UUID CURRENT_USER_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000000");
    private static final UUID OTHER_USER_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000000");

    @Mock private SharedGroupMembershipRepository sharedGroupMembershipRepository;
    @Spy
    private SharedGroupMemberCursorCodec cursorCodec =
            new SharedGroupMemberCursorCodec(new ObjectMapper());

    @InjectMocks private SharedGroupMemberService service;

    @Test
    void 활성_멤버가_아니면_SHARED_GROUP_NOT_FOUND_예외가_발생한다() {
        when(sharedGroupMembershipRepository.existsActiveMembership(
                        SHARED_GROUP_ID, CURRENT_USER_ID))
                .thenReturn(false);

        assertThatThrownBy(() -> service.findMembers(SHARED_GROUP_ID, CURRENT_USER_ID, null, 50))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(SharedGroupErrorCode.SHARED_GROUP_NOT_FOUND));
    }

    @Test
    void 멤버_목록을_커서_페이지로_응답한다() {
        Instant joinedAt = Instant.parse("2026-07-03T10:15:30Z");
        SharedGroupMemberRow me =
                new SharedGroupMemberRow(
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        CURRENT_USER_ID,
                        "집집이",
                        SharedGroupRole.HOST,
                        joinedAt);
        SharedGroupMemberRow other =
                new SharedGroupMemberRow(
                        UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        OTHER_USER_ID,
                        "친구",
                        SharedGroupRole.MEMBER,
                        joinedAt.plusSeconds(60));
        SharedGroupMemberRow extra =
                new SharedGroupMemberRow(
                        UUID.fromString("00000000-0000-0000-0000-000000000003"),
                        UUID.fromString("40000000-0000-0000-0000-000000000000"),
                        "다음",
                        SharedGroupRole.MEMBER,
                        joinedAt.plusSeconds(120));
        when(sharedGroupMembershipRepository.existsActiveMembership(
                        SHARED_GROUP_ID, CURRENT_USER_ID))
                .thenReturn(true);
        when(sharedGroupMembershipRepository.findActiveMembers(
                        eq(SHARED_GROUP_ID), any(Pageable.class)))
                .thenReturn(List.of(me, other, extra));
        SharedGroupMemberListResponse response =
                service.findMembers(SHARED_GROUP_ID, CURRENT_USER_ID, null, 2);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).isMe()).isTrue();
        assertThat(response.items().get(1).isMe()).isFalse();
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isNotBlank();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(sharedGroupMembershipRepository)
                .findActiveMembers(eq(SHARED_GROUP_ID), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(3);
    }
}
