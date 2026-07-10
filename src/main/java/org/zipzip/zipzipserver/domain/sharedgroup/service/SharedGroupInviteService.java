package org.zipzip.zipzipserver.domain.sharedgroup.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zipzip.zipzipserver.domain.sharedgroup.code.SharedGroupErrorCode;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.request.SharedGroupJoinRequest;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.InviteCodeResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupJoinResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupMembership;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupMembershipRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupRepository;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.domain.user.repository.AppUserRepository;
import org.zipzip.zipzipserver.global.code.GlobalErrorCode;
import org.zipzip.zipzipserver.global.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class SharedGroupInviteService {

    private static final int MAX_INVITE_CODE_LENGTH = 64;

    private final SharedGroupRepository sharedGroupRepository;
    private final SharedGroupMembershipRepository sharedGroupMembershipRepository;
    private final AppUserRepository appUserRepository;
    private final Clock clock = Clock.systemUTC();

    @Transactional(readOnly = true)
    public InviteCodeResponse findInviteCode(UUID sharedGroupId, UUID appUserId) {
        SharedGroup sharedGroup = findActiveSharedGroup(sharedGroupId);
        requireActiveMembership(sharedGroup.getId(), appUserId);

        return new InviteCodeResponse(
                sharedGroup.getId(), sharedGroup.getInviteCodeReservation().getInviteCode());
    }

    @Transactional
    public SharedGroupJoinResponse join(UUID appUserId, SharedGroupJoinRequest request) {
        String inviteCode = normalizeInviteCode(request.inviteCode());
        SharedGroup sharedGroup =
                sharedGroupRepository
                        .findActiveByInviteCode(inviteCode)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                SharedGroupErrorCode.INVALID_INVITE_CODE));

        if (sharedGroupMembershipRepository.existsActiveBySharedGroupIdAndAppUserId(
                sharedGroup.getId(), appUserId)) {
            throw new BusinessException(SharedGroupErrorCode.ALREADY_JOINED_SHARED_GROUP);
        }

        AppUser appUser =
                appUserRepository
                        .findById(appUserId)
                        .filter(user -> !user.isDeleted())
                        .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
        SharedGroupMembership membership =
                SharedGroupMembership.create(sharedGroup, appUser, SharedGroupRole.MEMBER);
        SharedGroupMembership savedMembership =
                sharedGroupMembershipRepository.saveAndFlush(membership);

        return new SharedGroupJoinResponse(
                sharedGroup.getId(),
                sharedGroup.getName(),
                savedMembership.getRole(),
                joinedAt(savedMembership));
    }

    @Transactional
    public void leave(UUID sharedGroupId, UUID appUserId) {
        SharedGroupMembership membership = requireActiveMembership(sharedGroupId, appUserId);
        if (membership.getRole() == SharedGroupRole.HOST) {
            throw new BusinessException(SharedGroupErrorCode.HOST_CANNOT_LEAVE_SHARED_GROUP);
        }

        sharedGroupMembershipRepository.delete(membership);
    }

    private SharedGroup findActiveSharedGroup(UUID sharedGroupId) {
        return sharedGroupRepository
                .findActiveWithInviteCodeById(sharedGroupId)
                .orElseThrow(
                        () -> new BusinessException(SharedGroupErrorCode.SHARED_GROUP_NOT_FOUND));
    }

    private SharedGroupMembership requireActiveMembership(UUID sharedGroupId, UUID appUserId) {
        return sharedGroupMembershipRepository
                .findActiveBySharedGroupIdAndAppUserId(sharedGroupId, appUserId)
                .orElseThrow(
                        () -> new BusinessException(SharedGroupErrorCode.SHARED_GROUP_NOT_FOUND));
    }

    private String normalizeInviteCode(String inviteCode) {
        String normalizedInviteCode = inviteCode == null ? "" : inviteCode.strip();
        if (normalizedInviteCode.isBlank()
                || normalizedInviteCode.length() > MAX_INVITE_CODE_LENGTH) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }
        return normalizedInviteCode;
    }

    private Instant joinedAt(SharedGroupMembership membership) {
        return membership.getCreatedAt() != null ? membership.getCreatedAt() : Instant.now(clock);
    }
}
