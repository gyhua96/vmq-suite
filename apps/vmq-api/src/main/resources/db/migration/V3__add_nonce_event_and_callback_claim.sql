create table if not exists request_nonce (
    id bigserial primary key,
    scope varchar(32) not null,
    nonce varchar(128) not null,
    expires_at int8 not null,
    created_at int8 not null,
    constraint uk_request_nonce_scope_nonce unique (scope, nonce)
);

create index if not exists idx_request_nonce_expires_at on request_nonce(expires_at);

alter table payment_event add column if not exists state int4 not null default 0;
alter table payment_event add column if not exists matched_order_id int8;

alter table callback_task add column if not exists claim_until int8 not null default 0;
-- Resolve duplicate callback_task.order_id rows before applying this index.
create unique index if not exists uk_callback_task_order_id on callback_task(order_id);
