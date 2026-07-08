create table photo_upload_reservation (
    object_key varchar(500) primary key,
    shared_album_id uuid not null,
    requested_by_app_user_id uuid not null,
    expires_at timestamptz not null,
    created_at timestamptz not null default now(),
    constraint fk_photo_upload_reservation__shared_album foreign key (shared_album_id)
        references shared_album (id) on delete cascade on update no action,
    constraint fk_photo_upload_reservation__requested_by_app_user foreign key (requested_by_app_user_id)
        references app_user (id) on delete restrict on update no action
);

create index idx_photo_upload_reservation__shared_album_id
    on photo_upload_reservation (shared_album_id);
create index idx_photo_upload_reservation__requested_by_app_user_id
    on photo_upload_reservation (requested_by_app_user_id);
create index idx_photo_upload_reservation__expires_at
    on photo_upload_reservation (expires_at);
