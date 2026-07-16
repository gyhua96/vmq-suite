-- Run duplicate_order_keys.sql and resolve every returned row before applying this migration.
-- The unique indexes intentionally fail fast when historical data is not clean.

alter table pay_order
    alter column price type numeric(19, 2) using round(price::numeric, 2),
    alter column really_price type numeric(19, 2) using round(really_price::numeric, 2);

alter table pay_qrcode
    alter column price type numeric(19, 2) using round(price::numeric, 2);

alter table payment_event
    alter column price type numeric(19, 2) using round(price::numeric, 2);

create unique index uk_pay_order_order_id on pay_order(order_id);
create unique index uk_pay_order_pay_id on pay_order(pay_id);
create unique index uk_pay_qrcode_type_price on pay_qrcode(type, price);
