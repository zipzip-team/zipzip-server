-- 사진 원본·썸네일을 영구 저장 URL 대신 Object Storage 키로 저장하고,
-- API는 조회 시점에 presigned URL을 발급한다. 썸네일은 비동기로 생성되므로
-- 진행 상태(thumbnail_status)를 함께 저장한다.

alter table photo
    rename column original_url to original_object_key;
alter table photo
    rename column thumbnail_url to thumbnail_object_key;

alter table photo
    add unique (original_object_key);

alter table photo
    add column thumbnail_status varchar(20) not null default 'PENDING';

alter table photo
    alter column thumbnail_status drop default;
