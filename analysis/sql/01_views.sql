-- Café Stecker analysis views.
SET client_min_messages = warning;
-- Safe to run again: every view is replaced.
-- All times in the tables are UTC; these views add Europe/Berlin local time.

CREATE SCHEMA IF NOT EXISTS analytics;

-- Indexes the analysis needs. The backend also declares them, so new
-- databases get them automatically; this covers older databases.
CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items (order_id);
CREATE INDEX IF NOT EXISTS idx_customer_orders_created_at ON customer_orders (created_at);

-- One row per order, with local time, the weather of its hour and
-- yes/no flags for events. Flags use EXISTS, so overlapping events never
-- duplicate an order.
CREATE OR REPLACE VIEW analytics.order_facts AS
SELECT
    o.id                                                   AS order_id,
    o.order_reference,
    o.data_source,
    o.status,
    o.created_at,
    o.confirmed_at,
    (o.created_at AT TIME ZONE 'Europe/Berlin')            AS local_time,
    (o.created_at AT TIME ZONE 'Europe/Berlin')::date      AS local_date,
    extract(hour FROM o.created_at AT TIME ZONE 'Europe/Berlin')::int    AS local_hour,
    extract(isodow FROM o.created_at AT TIME ZONE 'Europe/Berlin')::int  AS iso_weekday,
    extract(month FROM o.created_at AT TIME ZONE 'Europe/Berlin')::int   AS month,
    date_trunc('hour', o.created_at)                       AS hour_start_utc,
    o.total_price,
    (SELECT sum(i.quantity) FROM order_items i WHERE i.order_id = o.id)  AS items,
    w.temperature_c,
    w.precipitation_mm,
    (w.precipitation_mm >= 0.5)                            AS rainy_hour,
    EXISTS (SELECT 1 FROM local_events e
            WHERE e.type = 'CHRISTMAS_MARKET'
              AND o.created_at >= e.starts_at AND o.created_at < e.ends_at) AS christmas_market,
    EXISTS (SELECT 1 FROM local_events e
            WHERE e.type = 'FAIR'
              AND o.created_at >= e.starts_at AND o.created_at < e.ends_at) AS fair,
    EXISTS (SELECT 1 FROM local_events e
            WHERE e.type = 'FESTIVAL'
              AND o.created_at >= e.starts_at AND o.created_at < e.ends_at) AS festival,
    EXISTS (SELECT 1 FROM local_events e
            WHERE e.type = 'PARADE'
              AND o.created_at >= e.starts_at AND o.created_at < e.ends_at) AS parade,
    EXISTS (SELECT 1 FROM local_events e
            WHERE e.type = 'FOOTBALL'
              AND o.created_at >= e.starts_at - interval '4 hours'
              AND o.created_at <  e.starts_at - interval '30 minutes')        AS pre_match,
    EXISTS (SELECT 1 FROM local_events e
            WHERE e.type = 'FOOTBALL'
              AND o.created_at >= e.starts_at AND o.created_at < e.ends_at) AS during_match,
    EXISTS (SELECT 1 FROM local_events e
            WHERE e.type = 'FOOTBALL'
              AND (e.starts_at AT TIME ZONE 'Europe/Berlin')::date
                  = (o.created_at AT TIME ZONE 'Europe/Berlin')::date)         AS match_day
FROM customer_orders o
LEFT JOIN weather_hourly w ON w.observed_at = date_trunc('hour', o.created_at);

-- One row per order line, with the product's grouping from the menu.
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
    i.product_name || coalesce(' (' || m.size_volume || ')', '')  AS product_label,
    i.unit_price,
    i.quantity,
    i.line_total
FROM analytics.order_facts f
JOIN order_items i ON i.order_id = f.order_id
JOIN menu_items m ON m.id = i.menu_item_id;
