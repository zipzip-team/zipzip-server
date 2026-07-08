create table app_user (
    id uuid primary key,
    apple_subject varchar(255) not null unique,
    display_name varchar(50) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint chk_app_user__display_name_not_blank check (char_length(btrim(display_name)) > 0)
);

create table refresh_token (
    id uuid primary key,
    app_user_id uuid not null,
    token_hash varchar(255) not null unique,
    token_family_id uuid not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    replaced_by_refresh_token_id uuid unique,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fk_refresh_token__app_user foreign key (app_user_id)
        references app_user (id) on delete cascade on update no action,
    constraint fk_refresh_token__replaced_by foreign key (replaced_by_refresh_token_id)
        references refresh_token (id) on delete set null on update no action,
    constraint chk_refresh_token__expires_after_created check (expires_at > created_at),
    constraint chk_refresh_token__revoked_after_created check (revoked_at is null or revoked_at >= created_at)
);

create index idx_refresh_token__app_user_id_token_family_id
    on refresh_token (app_user_id, token_family_id);
create index idx_refresh_token__expires_at
    on refresh_token (expires_at);
create index idx_refresh_token__revoked_at
    on refresh_token (revoked_at);

create table invite_code_reservation (
    invite_code varchar(64) primary key,
    created_at timestamptz not null default now(),
    constraint chk_invite_code_reservation__invite_code_not_blank check (char_length(btrim(invite_code)) > 0)
);

create table shared_group (
    id uuid primary key,
    created_by_app_user_id uuid not null,
    name varchar(100) not null,
    invite_code varchar(64) not null unique,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint fk_shared_group__invite_code_reservation foreign key (invite_code)
        references invite_code_reservation (invite_code) on delete restrict on update no action,
    constraint fk_shared_group__created_by_app_user foreign key (created_by_app_user_id)
        references app_user (id) on delete restrict on update no action,
    constraint chk_shared_group__name_not_blank check (char_length(btrim(name)) > 0),
    constraint chk_shared_group__invite_code_not_blank check (char_length(btrim(invite_code)) > 0)
);

create index idx_shared_group__created_by_app_user_id_deleted_at
    on shared_group (created_by_app_user_id, deleted_at);
create index idx_shared_group__deleted_at
    on shared_group (deleted_at);

create table shared_group_membership (
    id uuid primary key,
    shared_group_id uuid not null,
    app_user_id uuid not null,
    role varchar(20) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fk_shared_group_membership__shared_group foreign key (shared_group_id)
        references shared_group (id) on delete cascade on update no action,
    constraint fk_shared_group_membership__app_user foreign key (app_user_id)
        references app_user (id) on delete restrict on update no action,
    constraint uk_shared_group_membership__group_user unique (shared_group_id, app_user_id),
    constraint chk_shared_group_membership__role check (role in ('HOST', 'MEMBER'))
);

create index idx_shared_group_membership__group_created
    on shared_group_membership (shared_group_id, created_at);
create index idx_shared_group_membership__user_created
    on shared_group_membership (app_user_id, created_at);
create index idx_shared_group_membership__group_role
    on shared_group_membership (shared_group_id, role);
create unique index uk_shared_group_membership__host
    on shared_group_membership (shared_group_id)
    where role = 'HOST';

create table device (
    id uuid primary key,
    app_user_id uuid not null,
    name varchar(100) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint fk_device__app_user foreign key (app_user_id)
        references app_user (id) on delete cascade on update no action,
    constraint chk_device__name_not_blank check (char_length(btrim(name)) > 0)
);

create index idx_device__app_user_id_deleted_at_created_at
    on device (app_user_id, deleted_at, created_at);
create index idx_device__user_name_deleted
    on device (app_user_id, name, deleted_at);
create unique index uk_device__active_name
    on device (app_user_id, lower(btrim(name)))
    where deleted_at is null;

create table shared_group_chat_message (
    id uuid primary key,
    shared_group_id uuid not null,
    app_user_id uuid not null,
    content varchar(1000) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint fk_shared_group_chat_message__shared_group foreign key (shared_group_id)
        references shared_group (id) on delete cascade on update no action,
    constraint fk_shared_group_chat_message__app_user foreign key (app_user_id)
        references app_user (id) on delete restrict on update no action,
    constraint chk_shared_group_chat_message__content_not_blank check (char_length(btrim(content)) > 0)
);

create index idx_shared_group_chat_message__group_active_created_id
    on shared_group_chat_message (shared_group_id, deleted_at, created_at, id);
create index idx_shared_group_chat_message__app_user_active_created
    on shared_group_chat_message (app_user_id, deleted_at, created_at);

create table shared_album (
    id uuid primary key,
    shared_group_id uuid not null,
    created_by_app_user_id uuid not null,
    name varchar(100) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint fk_shared_album__shared_group foreign key (shared_group_id)
        references shared_group (id) on delete cascade on update no action,
    constraint fk_shared_album__created_by_app_user foreign key (created_by_app_user_id)
        references app_user (id) on delete restrict on update no action,
    constraint chk_shared_album__name_not_blank check (char_length(btrim(name)) > 0)
);

create index idx_shared_album__shared_group_id_deleted_at_created_at
    on shared_album (shared_group_id, deleted_at, created_at);
create index idx_shared_album__created_by_app_user_id_deleted_at
    on shared_album (created_by_app_user_id, deleted_at);
create index idx_shared_album__deleted_at
    on shared_album (deleted_at);

create table photo (
    id uuid primary key,
    shared_album_id uuid not null,
    uploaded_by_app_user_id uuid not null,
    object_key text not null unique,
    original_file_name varchar(255) not null,
    content_type varchar(100) not null,
    file_size bigint not null,
    taken_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint fk_photo__shared_album foreign key (shared_album_id)
        references shared_album (id) on delete cascade on update no action,
    constraint fk_photo__uploaded_by_app_user foreign key (uploaded_by_app_user_id)
        references app_user (id) on delete restrict on update no action,
    constraint chk_photo__file_size_positive check (file_size > 0),
    constraint chk_photo__content_type_image check (content_type like 'image/%'),
    constraint chk_photo__original_file_name_not_blank check (char_length(btrim(original_file_name)) > 0)
);

create index idx_photo__album_active_id
    on photo (shared_album_id, deleted_at, id);
create index idx_photo__uploaded_by_app_user_id_deleted_at
    on photo (uploaded_by_app_user_id, deleted_at);
create index idx_photo__deleted_at
    on photo (deleted_at);
create index idx_photo__active_display_at
    on photo (shared_album_id, (coalesce(taken_at, created_at)), id)
    where deleted_at is null;

create table photo_like (
    id uuid primary key,
    photo_id uuid not null,
    app_user_id uuid not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fk_photo_like__photo foreign key (photo_id)
        references photo (id) on delete cascade on update no action,
    constraint fk_photo_like__app_user foreign key (app_user_id)
        references app_user (id) on delete cascade on update no action,
    constraint uk_photo_like__photo_id_app_user_id unique (photo_id, app_user_id)
);

create index idx_photo_like__app_user_id_created_at
    on photo_like (app_user_id, created_at);

create table photo_comment (
    id uuid primary key,
    photo_id uuid not null,
    app_user_id uuid not null,
    content varchar(1000) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint fk_photo_comment__photo foreign key (photo_id)
        references photo (id) on delete cascade on update no action,
    constraint fk_photo_comment__app_user foreign key (app_user_id)
        references app_user (id) on delete restrict on update no action,
    constraint chk_photo_comment__content_not_blank check (char_length(btrim(content)) > 0)
);

create index idx_photo_comment__photo_id_deleted_at_created_at
    on photo_comment (photo_id, deleted_at, created_at);
create index idx_photo_comment__app_user_id_deleted_at
    on photo_comment (app_user_id, deleted_at);
