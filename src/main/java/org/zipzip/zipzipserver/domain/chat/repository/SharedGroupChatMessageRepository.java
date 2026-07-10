package org.zipzip.zipzipserver.domain.chat.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.zipzip.zipzipserver.domain.chat.entity.SharedGroupChatMessage;

public interface SharedGroupChatMessageRepository
        extends JpaRepository<SharedGroupChatMessage, UUID> {

    @Query(
            value =
                    """
                    select timeline.id as id,
                           timeline.timeline_type as timeline_type,
                           timeline.photo_id as photo_id,
                           timeline.content as content,
                           timeline.author_id as author_id,
                           timeline.author_display_name as author_display_name,
                           timeline.created_at as created_at,
                           timeline.updated_at as updated_at
                    from (
                        select message.id as id,
                               'CHAT_MESSAGE' as timeline_type,
                               0 as timeline_type_order,
                               cast(null as uuid) as photo_id,
                               message.content as content,
                               author.id as author_id,
                               author.display_name as author_display_name,
                               message.created_at as created_at,
                               message.updated_at as updated_at
                        from shared_group_chat_message message
                        join app_user author on author.id = message.app_user_id
                        where message.shared_group_id = :sharedGroupId

                        union all

                        select comment.id as id,
                               'PHOTO_COMMENT' as timeline_type,
                               1 as timeline_type_order,
                               comment.photo_id as photo_id,
                               comment.content as content,
                               author.id as author_id,
                               author.display_name as author_display_name,
                               comment.created_at as created_at,
                               comment.updated_at as updated_at
                        from photo_comment comment
                        join photo on photo.id = comment.photo_id
                        join app_user author on author.id = comment.app_user_id
                        where photo.deleted_at is null
                          and exists (
                              select 1
                              from shared_album_photo album_photo
                              join shared_album album on album.id = album_photo.shared_album_id
                              where album_photo.photo_id = comment.photo_id
                                and album.shared_group_id = :sharedGroupId
                                and album.deleted_at is null
                          )
                    ) timeline
                    where cast(:cursorCreatedAt as timestamptz) is null
                       or timeline.created_at < cast(:cursorCreatedAt as timestamptz)
                       or (
                           timeline.created_at = cast(:cursorCreatedAt as timestamptz)
                           and (
                               timeline.timeline_type_order > cast(:cursorTypeOrder as integer)
                               or (
                                   timeline.timeline_type_order = cast(:cursorTypeOrder as integer)
                                   and timeline.id < cast(:cursorId as uuid)
                               )
                           )
                       )
                    order by timeline.created_at desc, timeline.timeline_type_order asc, timeline.id desc
                    """,
            nativeQuery = true)
    List<ChatTimelineItemProjection> findTimelineItems(
            @Param("sharedGroupId") UUID sharedGroupId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorTypeOrder") Integer cursorTypeOrder,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);
}
