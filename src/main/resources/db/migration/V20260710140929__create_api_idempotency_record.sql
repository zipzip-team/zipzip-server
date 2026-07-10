create table api_idempotency_record (
    id uuid primary key,
    scope varchar(100) not null,
    idempotency_key uuid not null,
    http_method varchar(10) not null,
    api_path varchar(255) not null,
    request_hash varchar(64) not null,
    status varchar(20) not null,
    response_http_status integer,
    encrypted_response_body text,
    expires_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_api_idempotency_record__scope_key_method_path
        unique (scope, idempotency_key, http_method, api_path),
    constraint chk_api_idempotency_record__status
        check (status in ('PROCESSING', 'COMPLETED')),
    constraint chk_api_idempotency_record__completed_response
        check (
            (status = 'PROCESSING' and response_http_status is null and encrypted_response_body is null)
            or (status = 'COMPLETED' and response_http_status is not null and encrypted_response_body is not null)
        )
);

create index idx_api_idempotency_record__expires_at
    on api_idempotency_record (expires_at);
