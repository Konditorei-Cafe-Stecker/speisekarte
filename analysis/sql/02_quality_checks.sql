-- Data-quality checks. Each row is one check; "problems" should be 0
-- unless the check is marked as information.
SELECT 'orders whose total differs from the sum of their lines' AS check_name,
       count(*) AS problems
FROM customer_orders o
WHERE o.total_price <> (SELECT coalesce(sum(i.line_total), 0)
                        FROM order_items i WHERE i.order_id = o.id)
UNION ALL
SELECT 'orders without lines', count(*)
FROM customer_orders o
WHERE NOT EXISTS (SELECT 1 FROM order_items i WHERE i.order_id = o.id)
UNION ALL
SELECT 'lines with quantity below 1', count(*)
FROM order_items WHERE quantity < 1
UNION ALL
SELECT 'lines whose total is not unit price × quantity', count(*)
FROM order_items WHERE line_total <> unit_price * quantity
UNION ALL
SELECT 'confirmed orders without confirmation time', count(*)
FROM customer_orders WHERE status = 'CONFIRMED' AND confirmed_at IS NULL
UNION ALL
SELECT 'confirmation time set on non-confirmed orders', count(*)
FROM customer_orders WHERE status <> 'CONFIRMED' AND confirmed_at IS NOT NULL
UNION ALL
SELECT 'confirmed before they were placed', count(*)
FROM customer_orders WHERE confirmed_at < created_at
UNION ALL
SELECT 'synthetic orders still pending', count(*)
FROM customer_orders WHERE data_source = 'SYNTHETIC' AND status = 'PENDING'
UNION ALL
SELECT 'orders without a weather observation for their hour', count(*)
FROM analytics.order_facts WHERE temperature_c IS NULL
UNION ALL
SELECT 'orders outside opening hours (Mon–Sat 9–17:30, Sun 13–17:30)', count(*)
FROM analytics.order_facts
WHERE local_time::time >= time '17:30'
   OR (iso_weekday < 7 AND local_time::time < time '09:00')
   OR (iso_weekday = 7 AND local_time::time < time '13:00')
UNION ALL
SELECT 'information: lines whose price differs from the current menu', count(*)
FROM order_items i JOIN menu_items m ON m.id = i.menu_item_id
WHERE i.unit_price <> m.price
UNION ALL
SELECT 'information: real (website) orders', count(*)
FROM customer_orders WHERE data_source = 'REAL';
