package org.zipzip.zipzipserver.domain.sharedgroup.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zipzip.zipzipserver.domain.album.entity.SharedAlbum;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumPhotoRepository;
import org.zipzip.zipzipserver.domain.album.repository.SharedAlbumRepository;
import org.zipzip.zipzipserver.domain.photo.entity.Photo;
import org.zipzip.zipzipserver.domain.sharedgroup.code.SharedGroupErrorCode;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.request.CreateSharedGroupRequest;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.CreateSharedGroupResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupDetailResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupListResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupSummaryResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupUpdateResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupUserSummaryResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.InviteCodeReservation;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroup;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupMembership;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.InviteCodeReservationRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupMembershipRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupQueryRepository;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupRepository;
import org.zipzip.zipzipserver.domain.user.entity.AppUser;
import org.zipzip.zipzipserver.domain.user.repository.AppUserRepository;
import org.zipzip.zipzipserver.global.code.GlobalErrorCode;
import org.zipzip.zipzipserver.global.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class SharedGroupService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int INVITE_CODE_MAX_ATTEMPTS = 10;

    private final AppUserRepository appUserRepository;
    private final InviteCodeReservationRepository inviteCodeReservationRepository;
    private final SharedGroupRepository sharedGroupRepository;
    private final SharedGroupMembershipRepository sharedGroupMembershipRepository;
    private final SharedGroupQueryRepository sharedGroupQueryRepository;
    private final SharedAlbumRepository sharedAlbumRepository;
    private final SharedAlbumPhotoRepository sharedAlbumPhotoRepository;
    private final SharedGroupCursorCodec sharedGroupCursorCodec;
    private final SharedGroupNameValidator sharedGroupNameValidator;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SharedGroupListResponse findMySharedGroups(UUID appUserId, String cursor, Integer size) {
        ensureActiveUser(appUserId);
        int pageSize = normalizePageSize(size);
        SharedGroupCursorCodec.SharedGroupCursor decodedCursor =
                sharedGroupCursorCodec.decode(cursor);

        List<SharedGroupQueryRepository.SharedGroupListRow> rows =
                sharedGroupQueryRepository.findMySharedGroups(
                        appUserId,
                        decodedCursor == null ? null : decodedCursor.joinedAt(),
                        decodedCursor == null ? null : decodedCursor.sharedGroupId(),
                        pageSize + 1);
        boolean hasNext = rows.size() > pageSize;
        List<SharedGroupQueryRepository.SharedGroupListRow> pageRows =
                hasNext ? rows.subList(0, pageSize) : rows;
        List<SharedGroupSummaryResponse> items =
                pageRows.stream().map(this::toSummaryResponse).toList();
        String nextCursor = null;
        if (hasNext && !pageRows.isEmpty()) {
            SharedGroupQueryRepository.SharedGroupListRow lastRow =
                    pageRows.get(pageRows.size() - 1);
            nextCursor = sharedGroupCursorCodec.encode(lastRow.joinedAt(), lastRow.id());
        }

        return new SharedGroupListResponse(items, nextCursor, hasNext);
    }

    @Transactional
    public CreateSharedGroupResponse createSharedGroup(
            UUID appUserId, CreateSharedGroupRequest request) {
        AppUser appUser = ensureActiveUser(appUserId);
        String normalizedName = sharedGroupNameValidator.normalize(request.name());
        InviteCodeReservation inviteCodeReservation = reserveInviteCode();
        SharedGroup sharedGroup =
                sharedGroupRepository.save(
                        SharedGroup.create(appUser, normalizedName, inviteCodeReservation));
        SharedGroupMembership membership =
                sharedGroupMembershipRepository.save(
                        SharedGroupMembership.create(sharedGroup, appUser, SharedGroupRole.HOST));
        sharedGroupMembershipRepository.flush();

        return new CreateSharedGroupResponse(
                sharedGroup.getId(),
                sharedGroup.getName(),
                inviteCodeReservation.getInviteCode(),
                membership.getRole(),
                new SharedGroupUserSummaryResponse(appUser.getId(), appUser.getDisplayName()),
                sharedGroup.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public SharedGroupDetailResponse findSharedGroup(UUID appUserId, UUID sharedGroupId) {
        ensureActiveUser(appUserId);
        return sharedGroupQueryRepository
                .findDetail(appUserId, sharedGroupId)
                .map(this::toDetailResponse)
                .orElseThrow(
                        () -> new BusinessException(SharedGroupErrorCode.SHARED_GROUP_NOT_FOUND));
    }

    @Transactional
    public SharedGroupUpdateResponse updateName(UUID appUserId, UUID sharedGroupId, String name) {
        String normalizedName = sharedGroupNameValidator.normalize(name);
        SharedGroupMembership membership = getActiveMembership(appUserId, sharedGroupId);
        validateHost(membership, SharedGroupOperation.UPDATE);
        SharedGroup sharedGroup = membership.getSharedGroup();
        sharedGroup.updateName(normalizedName);
        sharedGroupRepository.flush();
        return SharedGroupUpdateResponse.from(sharedGroup);
    }

    @Transactional
    public void delete(UUID appUserId, UUID sharedGroupId) {
        SharedGroupMembership membership = getActiveMembership(appUserId, sharedGroupId);
        validateHost(membership, SharedGroupOperation.DELETE);
        Instant deletedAt = Instant.now(clock);
        List<SharedAlbum> albums =
                sharedAlbumRepository.findBySharedGroupIdAndDeletedAtIsNull(sharedGroupId);
        List<Photo> photos =
                sharedAlbumPhotoRepository.findActivePhotosBySharedGroupId(sharedGroupId);

        albums.forEach(album -> album.delete(deletedAt));
        photos.forEach(photo -> photo.delete(deletedAt));
        membership.getSharedGroup().delete(deletedAt);
        sharedGroupRepository.flush();
    }

    private SharedGroupMembership getActiveMembership(UUID appUserId, UUID sharedGroupId) {
        SharedGroupMembership membership =
                sharedGroupMembershipRepository
                        .findWithSharedGroupAndAppUserBySharedGroupIdAndAppUserId(
                                sharedGroupId, appUserId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                SharedGroupErrorCode.SHARED_GROUP_NOT_FOUND));
        if (membership.getSharedGroup().getDeletedAt() != null
                || membership.getAppUser().isDeleted()) {
            throw new BusinessException(SharedGroupErrorCode.SHARED_GROUP_NOT_FOUND);
        }
        return membership;
    }

    private void validateHost(
            SharedGroupMembership membership, SharedGroupOperation sharedGroupOperation) {
        if (membership.getRole() != SharedGroupRole.HOST) {
            throw new BusinessException(sharedGroupOperation.errorCode());
        }
    }

    private AppUser ensureActiveUser(UUID appUserId) {
        return appUserRepository
                .findByIdAndDeletedAtIsNull(appUserId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
    }

    private int normalizePageSize(Integer size) {
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }
        return pageSize;
    }

    private InviteCodeReservation reserveInviteCode() {
        for (int attempt = 0; attempt < INVITE_CODE_MAX_ATTEMPTS; attempt++) {
            String inviteCode = inviteCodeGenerator.generate();
            if (inviteCodeReservationRepository.insertIfAbsent(inviteCode) == 1) {
                return inviteCodeReservationRepository.getReferenceById(inviteCode);
            }
        }

        throw new BusinessException(SharedGroupErrorCode.INVITE_CODE_GENERATION_FAILED);
    }

    private SharedGroupSummaryResponse toSummaryResponse(
            SharedGroupQueryRepository.SharedGroupListRow row) {
        return new SharedGroupSummaryResponse(
                row.id(),
                row.name(),
                row.myRole(),
                row.memberCount(),
                row.sharedAlbumCount(),
                row.photoCount(),
                row.joinedAt(),
                row.updatedAt());
    }

    private SharedGroupDetailResponse toDetailResponse(
            SharedGroupQueryRepository.SharedGroupDetailRow row) {
        return new SharedGroupDetailResponse(
                row.id(),
                row.name(),
                row.myRole(),
                new SharedGroupUserSummaryResponse(
                        row.createdByUserId(), row.createdByDisplayName()),
                row.memberCount(),
                row.sharedAlbumCount(),
                row.photoCount(),
                row.createdAt(),
                row.updatedAt());
    }

    private enum SharedGroupOperation {
        UPDATE(SharedGroupErrorCode.ONLY_HOST_CAN_UPDATE_SHARED_GROUP),
        DELETE(SharedGroupErrorCode.ONLY_HOST_CAN_DELETE_SHARED_GROUP);

        private final SharedGroupErrorCode errorCode;

        SharedGroupOperation(SharedGroupErrorCode errorCode) {
            this.errorCode = errorCode;
        }

        private SharedGroupErrorCode errorCode() {
            return errorCode;
        }
    }
}
