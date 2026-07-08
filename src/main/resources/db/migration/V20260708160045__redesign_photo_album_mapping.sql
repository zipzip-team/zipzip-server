-- 사진을 공유집(앨범)에 N:M으로 연결하도록 재설계하고,
-- 사진 메타데이터(썸네일, 촬영 기기, 위치, 크기)를 추가한다.

drop index if exists idx_photo__album_active_id;
drop index if exists idx_photo__active_display_at;

alter table photo
    drop constraint fk_photo__shared_album,
    drop constraint chk_photo__file_size_positive,
    drop constraint chk_photo__content_type_image,
    drop constraint chk_photo__original_file_name_not_blank;

alter table photo
    drop column shared_album_id,
    drop column object_key,
    drop column original_file_name,
    drop column content_type,
    drop column file_size;

alter table photo
    add column device_id uuid,
    add column original_url varchar(500),
    add column thumbnail_url varchar(500),
    add column latitude double precision,
    add column longitude double precision,
    add column location_name varchar(200),
    add column is_inferred boolean not null default false,
    add column width int,
    add column height int;

alter table photo
    alter column original_url set not null;

alter table photo
    add constraint fk_photo__device foreign key (device_id)
        references device (id) on delete set null on update no action;

create index idx_photo__active_display_at
    on photo ((coalesce(taken_at, created_at)), id)
    where deleted_at is null;

create table shared_album_photo (
    id uuid primary key,
    shared_album_id uuid not null,
    photo_id uuid not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fk_shared_album_photo__shared_album foreign key (shared_album_id)
        references shared_album (id) on delete cascade on update no action,
    constraint fk_shared_album_photo__photo foreign key (photo_id)
        references photo (id) on delete cascade on update no action,
    constraint uk_shared_album_photo__shared_album_id_photo_id unique (shared_album_id, photo_id)
);

create index idx_shared_album_photo__shared_album_id_created_at
    on shared_album_photo (shared_album_id, created_at);
create index idx_shared_album_photo__photo_id
    on shared_album_photo (photo_id);
