-- Baseline performance indexes for the current JPA-managed schema.
-- Unique indexes are intentionally left out of V1 because existing deployments
-- may contain historical duplicate pay_id/order_id rows. Run the preflight
-- duplicate checks before adding uniqueness in a later migration.

create index if not exists idx_pay_order_order_id on pay_order(order_id);
create index if not exists idx_pay_order_pay_id on pay_order(pay_id);
create index if not exists idx_pay_order_match on pay_order(type, really_price, state);
create index if not exists idx_pay_order_create_date on pay_order(create_date);
create index if not exists idx_pay_order_state on pay_order(state);
create index if not exists idx_pay_order_pay_date on pay_order(pay_date);

create index if not exists idx_pay_qrcode_type_price on pay_qrcode(type, price);

create table if not exists payment_event (
    id bigserial,
    event_key varchar(128) not null,
    type int4 not null,
    price float8 not null,
    event_time int8 not null,
    received_at int8 not null,
    primary key (id)
);

create unique index if not exists uk_payment_event_event_key on payment_event(event_key);
create index if not exists idx_payment_event_event_time on payment_event(event_time);

create table if not exists callback_task (
    id bigserial,
    order_id int8 not null,
    pay_id varchar(128) not null,
    notify_url varchar(1024) not null,
    query varchar(2048) not null,
    -- 0=pending, 1=success, 2=retry waiting, 3=final failed
    state int4 not null,
    retry_count int4 not null,
    next_retry_time int8 not null,
    create_time int8 not null,
    update_time int8 not null,
    last_response varchar(1024),
    last_error varchar(255),
    primary key (id)
);

create index if not exists idx_callback_task_order_id on callback_task(order_id);
create index if not exists idx_callback_task_retry on callback_task(state, next_retry_time);
