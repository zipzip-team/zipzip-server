-- 사진 댓글과 그룹 채팅 메시지는 soft delete 대신 즉시 물리 삭제로 전환한다.
-- (기록용 활동 피드 성격상 삭제된 항목은 조회 대상에서 완전히 사라져야 하고,
--  30일 유예 정리 대상에서도 제외한다.)

drop index if exists idx_shared_group_chat_message__group_active_created_id;
drop index if exists idx_shared_group_chat_message__app_user_active_created;

alter table shared_group_chat_message
    drop column deleted_at;

create index idx_shared_group_chat_message__group_created_id
    on shared_group_chat_message (shared_group_id, created_at, id);
create index idx_shared_group_chat_message__app_user_id_created_at
    on shared_group_chat_message (app_user_id, created_at);

drop index if exists idx_photo_comment__photo_id_deleted_at_created_at;
drop index if exists idx_photo_comment__app_user_id_deleted_at;

alter table photo_comment
    drop column deleted_at;

create index idx_photo_comment__photo_id_created_at
    on photo_comment (photo_id, created_at);
create index idx_photo_comment__app_user_id_created_at
    on photo_comment (app_user_id, created_at);
