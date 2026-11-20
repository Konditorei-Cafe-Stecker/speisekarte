-- Café Stecker analysis views.
SET client_min_messages = warning;
-- Safe to run again: every view is replaced.
--
-- customer_orders.created_at is Bremen local time (timestamp without time
-- zone); handled_at, weather and events are UTC (timestamp with time zone).
-- The views offer both, so local hours and joins with weather are correct.

CREATE SCHEMA IF NOT EXISTS analytics;

-- One row per order, with local time, the weather of its hour and
-- yes/no flags for events. Flags use EXISTS, so overlapping events never
-- duplicate an order.
CREATE OR REPLACE VIEW analytics.order_facts AS
SELECT
    o.id                                    AS order_id,
    o.order_reference,
    o.data_source,
    o.status,
    u.created_utc                           AS created_at,
    o.handled_at,
    o.created_at                            AS local_time,
    o.created_at::date                      AS local_date,
    extract(hour FROM o.created_at)::int    AS local_hour,
    extract(isodow FROM o.created_at)::int  AS iso_weekday,
    extract(month FROM o.created_at)::int   AS month,
    date_trunc('hour', u.created_utc)       AS hour_start_utc,
    o.total_price,
    -- Lines a waiter removed keep quantity 0 and add nothing here.
    (SELECT sum(i.quantity) FROM order_items i WHERE i.order_id = o.id) AS items,
    w.temperature_c,
    w.precipitation_mm,
    (w.precipitation_mm >= 0.5)             AS rainy_hour,
    EXISTS (SELECT 1 FROM local_events e
            WHERE e.type = 'CHRISTMAS_MARKET'
              AND u.created_utc >= e.starts_at AND u.created_utc < e.ends_at) AS christmas_market,
    EXISTS (SELECT 1 FROM local_events e
            WHERE e.type = 'FAIR'
              AND u.created_utc >= e.starts_at AND u.created_utc < e.ends_at) AS fair,
    EXISTS (SELECT 1 FROM local_events e
            WHERE e.type = 'FESTIVAL'
              AND u.created_utc >= e.starts_at AND u.created_utc < e.ends_at) AS festival,
    EXISTS (SELECT 1 FROM local_events e
            WHERE e.type = 'PARADE'
              AND u.created_utc >= e.starts_at AND u.created_utc < e.ends_at) AS parade,
    EXISTS (SELECT 1 FROM local_events e
            WHERE e.type = 'FOOTBALL'
              AND u.created_utc >= e.starts_at - interval '4 hours'
              AND u.created_utc <  e.starts_at - interval '30 minutes')       AS pre_match,
    EXISTS (SELECT 1 FROM local_events e
            WHERE e.type = 'FOOTBALL'
              AND u.created_utc >= e.starts_at AND u.created_utc < e.ends_at) AS during_match,
    EXISTS (SELECT 1 FROM local_events e
            WHERE e.type = 'FOOTBALL'
              AND (e.starts_at AT TIME ZONE 'Europe/Berlin')::date = o.created_at::date) AS match_day
FROM customer_orders o
-- The Bremen local time as a moment in UTC, for joins with weather and events.
CROSS JOIN LATERAL (SELECT o.created_at AT TIME ZONE 'Europe/Berlin' AS created_utc) u
LEFT JOIN weather_hourly w ON w.observed_at = date_trunc('hour', u.created_utc);

-- One row per order line of the final order, with the product's grouping.
-- product_label keeps size variants apart (e.g. the two Apollinaris sizes).
CREATE OR REPLACE VIEW analytics.order_line_facts AS
SELECT
    f.*,
    i.id                         AS line_id,
    i.menu_item_id,
    i.product_name,
    m.category,
    m.subcategory,
    m.size_volume,
    CASE WHEN m.size_volume IS NULL OR strpos(i.product_name, m.size_volume) > 0
         THEN i.product_name
         ELSE i.product_name || ' (' || m.size_volume || ')' END AS product_label,
    i.unit_price,
    i.quantity,
    i.original_quantity,
    i.line_total
FROM analytics.order_facts f
JOIN order_items i ON i.order_id = f.order_id
JOIN menu_items m ON m.id = i.menu_item_id
-- Lines the waiter removed (quantity 0) are not part of the final order.
WHERE i.quantity > 0;
