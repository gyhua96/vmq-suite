-- Run before adding unique indexes to pay_order(order_id) and pay_order(pay_id).
-- These queries must return zero rows before uniqueness can be enforced safely.

select order_id, count(*) as duplicate_count
from pay_order
where order_id is not null and order_id <> ''
group by order_id
having count(*) > 1
order by duplicate_count desc, order_id;

select pay_id, count(*) as duplicate_count
from pay_order
where pay_id is not null and pay_id <> ''
group by pay_id
having count(*) > 1
order by duplicate_count desc, pay_id;

select type, price, count(*) as duplicate_count
from pay_qrcode
group by type, price
having count(*) > 1
order by duplicate_count desc, type, price;

select order_id, count(*) as duplicate_count
from callback_task
where order_id is not null
group by order_id
having count(*) > 1
order by duplicate_count desc, order_id;
