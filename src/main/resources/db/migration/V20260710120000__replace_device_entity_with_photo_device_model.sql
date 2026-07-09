-- 기기를 별도 엔티티로 관리하지 않고, 사진 메타데이터에서 촬영 기기명만 저장한다.

alter table photo
    drop constraint fk_photo__device;

alter table photo
    drop column device_id,
    add column device_model varchar(100);

drop table device;
