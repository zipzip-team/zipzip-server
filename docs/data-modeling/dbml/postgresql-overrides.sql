-- DBML이 표현하지 못하는 PostgreSQL partial/expression index를 정의한다.
-- zipzip.dbml로 생성한 기본 스키마에 이 파일을 추가 적용해야 물리 모델이 완성된다.

create unique index if not exists uk_shared_group_membership__host
    on shared_group_membership (shared_group_id)
    where role = 'HOST';

create unique index if not exists uk_device__active_name
    on device (app_user_id, lower(btrim(name)))
    where deleted_at is null;

create index if not exists idx_photo__active_display_at
    on photo (shared_album_id, (coalesce(taken_at, created_at)), id)
    where deleted_at is null;
