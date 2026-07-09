package org.zipzip.zipzipserver.domain.sharedgroup.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zipzip.zipzipserver.domain.device.repository.DeviceRepository;
import org.zipzip.zipzipserver.domain.device.repository.DeviceRow;
import org.zipzip.zipzipserver.domain.sharedgroup.code.SharedGroupErrorCode;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupMemberListResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupMemberRow;
import org.zipzip.zipzipserver.domain.sharedgroup.repository.SharedGroupMembershipRepository;
import org.zipzip.zipzipserver.global.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class SharedGroupMemberService {

    private final SharedGroupMembershipRepository sharedGroupMembershipRepository;
    private final DeviceRepository deviceRepository;
    private final SharedGroupMemberCursorCodec cursorCodec;

    @Transactional(readOnly = true)
    public SharedGroupMemberListResponse findMembers(
            UUID sharedGroupId, UUID currentAppUserId, String cursor, int size) {
        if (!sharedGroupMembershipRepository.existsActiveMembership(
                sharedGroupId, currentAppUserId)) {
            throw new BusinessException(SharedGroupErrorCode.SHARED_GROUP_NOT_FOUND);
        }

        SharedGroupMemberCursor decodedCursor = cursorCodec.decode(cursor);
        PageRequest pageRequest = PageRequest.of(0, size + 1);
        List<SharedGroupMemberRow> rows;
        if (decodedCursor == null) {
            rows = sharedGroupMembershipRepository.findActiveMembers(sharedGroupId, pageRequest);
        } else {
            rows =
                    sharedGroupMembershipRepository.findActiveMembersAfter(
                            sharedGroupId,
                            decodedCursor.joinedAt(),
                            decodedCursor.membershipId(),
                            pageRequest);
        }

        boolean hasNext = rows.size() > size;
        List<SharedGroupMemberRow> pageRows =
                hasNext ? new ArrayList<>(rows.subList(0, size)) : rows;
        Map<UUID, List<SharedGroupMemberListResponse.Device>> devicesByUserId =
                findDevicesByUserId(pageRows);
        List<SharedGroupMemberListResponse.Member> items =
                pageRows.stream()
                        .map(row -> toMember(row, currentAppUserId, devicesByUserId))
                        .toList();

        String nextCursor = null;
        if (hasNext && !pageRows.isEmpty()) {
            SharedGroupMemberRow lastRow = pageRows.get(pageRows.size() - 1);
            nextCursor =
                    cursorCodec.encode(
                            new SharedGroupMemberCursor(
                                    lastRow.joinedAt(), lastRow.membershipId()));
        }

        return new SharedGroupMemberListResponse(items, nextCursor, hasNext);
    }

    private Map<UUID, List<SharedGroupMemberListResponse.Device>> findDevicesByUserId(
            List<SharedGroupMemberRow> rows) {
        if (rows.isEmpty()) {
            return Collections.emptyMap();
        }

        List<UUID> userIds = rows.stream().map(SharedGroupMemberRow::userId).toList();
        return deviceRepository.findActiveDevicesByAppUserIds(userIds).stream()
                .collect(
                        Collectors.groupingBy(
                                DeviceRow::appUserId,
                                Collectors.mapping(
                                        row ->
                                                new SharedGroupMemberListResponse.Device(
                                                        row.id(), row.name()),
                                        Collectors.toList())));
    }

    private SharedGroupMemberListResponse.Member toMember(
            SharedGroupMemberRow row,
            UUID currentAppUserId,
            Map<UUID, List<SharedGroupMemberListResponse.Device>> devicesByUserId) {
        return new SharedGroupMemberListResponse.Member(
                row.userId(),
                row.displayName(),
                row.role(),
                row.userId().equals(currentAppUserId),
                row.joinedAt(),
                devicesByUserId.getOrDefault(row.userId(), List.of()));
    }
}
