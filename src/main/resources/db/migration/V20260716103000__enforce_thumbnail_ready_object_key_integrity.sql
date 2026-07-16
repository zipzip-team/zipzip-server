-- 기존의 잘못된 READY 행은 썸네일 재시도 대상인 FAILED로 정리한 뒤,
-- READY 상태가 비어 있지 않은 객체 키를 반드시 갖도록 보장한다.
update photo
set thumbnail_status = 'FAILED'
where thumbnail_status = 'READY'
  and (
      thumbnail_object_key is null
      or thumbnail_object_key ~ '^[[:space:]]*$'
  );

alter table photo
    add constraint chk_photo__thumbnail_ready_object_key
    check (
        thumbnail_status <> 'READY'
        or (
            thumbnail_object_key is not null
            and thumbnail_object_key !~ '^[[:space:]]*$'
        )
    );
