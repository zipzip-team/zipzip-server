package org.zipzip.zipzipserver.domain.sharedgroup.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumPhotoRepository;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.sharedgroup.code.SharedGroupErrorCode;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.request.SharedGroupJoinRequest;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.InviteCodeResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupJoinPreviewResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupJoinResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupMembership;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupMembershipRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupRepository;
import org.zipzip.zipzipserver.domain.storage.ObjectStorageService;
import org.zipzip.zipzipserver.domain.storage.PresignedDownload;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.domain.user.repository.AppUserRepository;
import org.zipzip.zipzipserver.global.code.GlobalErrorCode;
import org.zipzip.zipzipserver.global.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class SharedGroupInviteService {

    private static final int MAX_INVITE_CODE_LENGTH = 64;
    private static final int PREVIEW_MEMBER_LIMIT = 5;
    private static final Duration REPRESENTATIVE_IMAGE_URL_TTL = Duration.ofMinutes(10);

    private final SharedGroupRepository sharedGroupRepository;
    private final SharedGroupMembershipRepository sharedGroupMembershipRepository;
    private final SharedAlbumPhotoRepository sharedAlbumPhotoRepository;
    private final AppUserRepository appUserRepository;
    private final ObjectStorageService objectStorageService;
    private final Clock clock = Clock.systemUTC();

    @Transactional(readOnly = true)
    public InviteCodeResponse findInviteCode(UUID sharedGroupId, UUID appUserId) {
        SharedGroup sharedGroup = findActiveSharedGroup(sharedGroupId);
        requireActiveMembership(sharedGroup.getId(), appUserId);

        return new InviteCodeResponse(
                sharedGroup.getId(), sharedGroup.getInviteCodeReservation().getInviteCode());
    }

    @Transactional(readOnly = true)
    public SharedGroupJoinPreviewResponse previewJoin(UUID appUserId, String inviteCode) {
        requireActiveUser(appUserId);
        String normalizedInviteCode = normalizeInviteCode(inviteCode);
        SharedGroup sharedGroup =
                sharedGroupRepository
                        .findActiveWithCreatorByInviteCode(normalizedInviteCode)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                SharedGroupErrorCode.INVALID_INVITE_CODE));
        List<SharedGroupJoinPreviewResponse.Member> members =
                sharedGroupMembershipRepository
                        .findActiveMembers(
                                sharedGroup.getId(), PageRequest.of(0, PREVIEW_MEMBER_LIMIT))
                        .stream()
                        .map(
                                member ->
                                        new SharedGroupJoinPreviewResponse.Member(
                                                member.userId(),
                                                member.displayName(),
                                                member.role()))
                        .toList();
        PresignedDownload representativeImage = findRepresentativeImage(sharedGroup.getId());

        return new SharedGroupJoinPreviewResponse(
                sharedGroup.getId(),
                sharedGroup.getName(),
                representativeImage == null ? null : representativeImage.url(),
                representativeImage == null ? null : representativeImage.expiresAt(),
                new org.zipzip.zipzipserver.domain.sharedgroup.dto.response
                        .SharedGroupUserSummaryResponse(
                        sharedGroup.getCreatedByAppUser().getId(),
                        sharedGroup.getCreatedByAppUser().getDisplayName()),
                sharedGroupMembershipRepository.countActiveMembers(sharedGroup.getId()),
                members,
                sharedGroupMembershipRepository.existsActiveBySharedGroupIdAndAppUserId(
                        sharedGroup.getId(), appUserId));
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

        AppUser appUser = requireActiveUser(appUserId);
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

    private AppUser requireActiveUser(UUID appUserId) {
        return appUserRepository
                .findById(appUserId)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
    }

    private PresignedDownload findRepresentativeImage(UUID sharedGroupId) {
        return sharedAlbumPhotoRepository
                .findRepresentativePhotoBySharedGroupId(sharedGroupId, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(this::issueRepresentativeImageDownload)
                .orElse(null);
    }

    private PresignedDownload issueRepresentativeImageDownload(Photo photo) {
        String objectKey =
                photo.hasUsableThumbnail()
                        ? photo.getThumbnailObjectKey()
                        : photo.getOriginalObjectKey();
        return objectStorageService.issueDownloadUrl(objectKey, REPRESENTATIVE_IMAGE_URL_TTL);
    }

    private Instant joinedAt(SharedGroupMembership membership) {
        return membership.getCreatedAt() != null ? membership.getCreatedAt() : Instant.now(clock);
    }
}
