package org.zipzip.zipzipserver.domain.sharedgroup.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.zipzip.zipzipserver.domain.sharedgroup.entity.SharedGroupRole;

@Repository
@RequiredArgsConstructor
public class SharedGroupQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public List<SharedGroupListRow> findMySharedGroups(
            UUID appUserId, Instant cursorJoinedAt, UUID cursorSharedGroupId, int limit) {
        Map<String, Object> params = new HashMap<>();
        params.put("appUserId", appUserId);
        params.put("limit", limit);
        params.put(
                "cursorJoinedAt", cursorJoinedAt == null ? null : Timestamp.from(cursorJoinedAt));
        params.put("cursorSharedGroupId", cursorSharedGroupId);

        String cursorCondition = "";
        if (cursorJoinedAt != null && cursorSharedGroupId != null) {
            cursorCondition =
                    """
                    and (
                        sgm.created_at < :cursorJoinedAt
                        or (sgm.created_at = :cursorJoinedAt and sg.id > :cursorSharedGroupId)
                    )
                    """;
        }

        return jdbcTemplate.query(
                """
                select
                    sg.id,
                    sg.name,
                    sgm.role as my_role,
                    sgm.created_at as joined_at,
                    sg.updated_at,
                    coalesce(members.member_count, 0) as member_count,
                    coalesce(members.member_names, array[]::varchar[]) as member_names,
                    (
                        select count(*)
                        from shared_album sa
                        where sa.shared_group_id = sg.id
                          and sa.deleted_at is null
                    ) as shared_album_count,
                    (
                        select count(distinct sap.photo_id)
                        from shared_album sa
                        join shared_album_photo sap on sap.shared_album_id = sa.id
                        join photo p on p.id = sap.photo_id
                        where sa.shared_group_id = sg.id
                          and sa.deleted_at is null
                          and p.deleted_at is null
                    ) as photo_count
                from shared_group_membership sgm
                join shared_group sg on sg.id = sgm.shared_group_id
                left join lateral (
                    select
                        count(*) as member_count,
                        array_agg(
                            member_user.display_name
                            order by member_sgm.created_at asc, member_sgm.id asc
                        ) as member_names
                    from shared_group_membership member_sgm
                    join app_user member_user on member_user.id = member_sgm.app_user_id
                    where member_sgm.shared_group_id = sg.id
                      and member_user.deleted_at is null
                ) members on true
                where sgm.app_user_id = :appUserId
                  and sg.deleted_at is null
                """
                        + cursorCondition
                        + """
                        order by sgm.created_at desc, sg.id asc
                        limit :limit
                        """,
                params,
                this::mapListRow);
    }

    public Optional<SharedGroupDetailRow> findDetail(UUID appUserId, UUID sharedGroupId) {
        Map<String, Object> params = Map.of("appUserId", appUserId, "sharedGroupId", sharedGroupId);
        List<SharedGroupDetailRow> rows =
                jdbcTemplate.query(
                        """
                        select
                            sg.id,
                            sg.name,
                            sgm.role as my_role,
                            created_by.id as created_by_user_id,
                            created_by.display_name as created_by_display_name,
                            sg.created_at,
                            sg.updated_at,
                            (
                                select count(*)
                                from shared_group_membership member_sgm
                                where member_sgm.shared_group_id = sg.id
                            ) as member_count,
                            (
                                select count(*)
                                from shared_album sa
                                where sa.shared_group_id = sg.id
                                  and sa.deleted_at is null
                            ) as shared_album_count,
                            (
                                select count(distinct sap.photo_id)
                                from shared_album sa
                                join shared_album_photo sap on sap.shared_album_id = sa.id
                                join photo p on p.id = sap.photo_id
                                where sa.shared_group_id = sg.id
                                  and sa.deleted_at is null
                                  and p.deleted_at is null
                            ) as photo_count
                        from shared_group_membership sgm
                        join shared_group sg on sg.id = sgm.shared_group_id
                        join app_user created_by on created_by.id = sg.created_by_app_user_id
                        where sgm.app_user_id = :appUserId
                          and sg.id = :sharedGroupId
                          and sg.deleted_at is null
                        """,
                        params,
                        this::mapDetailRow);
        return rows.stream().findFirst();
    }

    private SharedGroupListRow mapListRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SharedGroupListRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("name"),
                SharedGroupRole.valueOf(resultSet.getString("my_role")),
                resultSet.getLong("member_count"),
                Arrays.asList((String[]) resultSet.getArray("member_names").getArray()),
                resultSet.getLong("shared_album_count"),
                resultSet.getLong("photo_count"),
                resultSet.getTimestamp("joined_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private SharedGroupDetailRow mapDetailRow(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new SharedGroupDetailRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("name"),
                SharedGroupRole.valueOf(resultSet.getString("my_role")),
                resultSet.getObject("created_by_user_id", UUID.class),
                resultSet.getString("created_by_display_name"),
                resultSet.getLong("member_count"),
                resultSet.getLong("shared_album_count"),
                resultSet.getLong("photo_count"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    public record SharedGroupListRow(
            UUID id,
            String name,
            SharedGroupRole myRole,
            long memberCount,
            List<String> memberNames,
            long sharedAlbumCount,
            long photoCount,
            Instant joinedAt,
            Instant updatedAt) {}

    public record SharedGroupDetailRow(
            UUID id,
            String name,
            SharedGroupRole myRole,
            UUID createdByUserId,
            String createdByDisplayName,
            long memberCount,
            long sharedAlbumCount,
            long photoCount,
            Instant createdAt,
            Instant updatedAt) {}
}
