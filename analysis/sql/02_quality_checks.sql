-- Data-quality checks. Each row is one check; "problems" should be 0
-- unless the check is marked as information.
SELECT 'orders whose total differs from the sum of their lines' AS check_name,
       count(*) AS problems
FROM customer_orders o
WHERE o.total_price <> (SELECT coalesce(sum(i.line_total), 0)
                        FROM order_items i WHERE i.order_id = o.id)
UNION ALL
SELECT 'confirmed orders with nothing left on them', count(*)
FROM customer_orders o
WHERE o.status = 'CONFIRMED'
  AND NOT EXISTS (SELECT 1 FROM order_items i WHERE i.order_id = o.id AND i.quantity > 0)
UNION ALL
SELECT 'lines with a negative quantity', count(*)
FROM order_items WHERE quantity < 0 OR original_quantity < 0
UNION ALL
SELECT 'lines whose total is not unit price × quantity', count(*)
FROM order_items WHERE line_total <> unit_price * quantity
UNION ALL
SELECT 'confirmed or cancelled orders without a handled time', count(*)
FROM customer_orders WHERE status IN ('CONFIRMED', 'CANCELLED') AND handled_at IS NULL
UNION ALL
SELECT 'pending orders with a handled time', count(*)
FROM customer_orders WHERE status = 'PENDING' AND handled_at IS NOT NULL
UNION ALL
SELECT 'handled before they were placed', count(*)
FROM customer_orders WHERE handled_at < (created_at AT TIME ZONE 'Europe/Berlin')
UNION ALL
SELECT 'synthetic orders still pending', count(*)
FROM customer_orders WHERE data_source = 'SYNTHETIC' AND status = 'PENDING'
UNION ALL
SELECT 'orders without a weather observation for their hour', count(*)
FROM analytics.order_facts WHERE data_source = 'SYNTHETIC' AND temperature_c IS NULL
UNION ALL
SELECT 'orders outside opening hours (Mon–Sat 9–17:30, Sun 13–17:30)', count(*)
FROM analytics.order_facts
WHERE data_source = 'SYNTHETIC'
  AND (local_time::time >= time '17:30'
   OR (iso_weekday < 7 AND local_time::time < time '09:00')
   OR (iso_weekday = 7 AND local_time::time < time '13:00'))
UNION ALL
SELECT 'information: lines a waiter changed', count(*)
FROM order_items WHERE quantity <> original_quantity
UNION ALL
SELECT 'information: lines whose price differs from the current menu', count(*)
FROM order_items i JOIN menu_items m ON m.id = i.menu_item_id
WHERE i.unit_price <> m.price
UNION ALL
SELECT 'information: real (website) orders', count(*)
FROM customer_orders WHERE data_source = 'REAL';
