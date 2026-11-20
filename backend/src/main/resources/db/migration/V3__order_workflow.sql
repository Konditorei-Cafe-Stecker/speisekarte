-- Version 3: what the waiter's side of an order needs.
--
-- customer_orders
--   short_code     4 characters the guest can read out when the QR code
--                  does not scan (only unique among open orders)
--   guest_note     wishes the guest typed in, for the waiter
--   handled_at/_by who confirmed or cancelled the order, and when
--   version        optimistic locking: two waiters cannot overwrite each
--                  other's changes
--
-- order_items
--   original_quantity  what the guest ordered; quantity is what the waiter
--                      confirmed. Lines added by the waiter have 0, lines
--                      the waiter removed keep their row with quantity 0.
--
-- Orders saved before this version keep an empty short_code and note.

ALTER TABLE customer_orders ADD COLUMN short_code varchar(8);
ALTER TABLE customer_orders ADD COLUMN guest_note varchar(300);
ALTER TABLE customer_orders ADD COLUMN handled_at timestamp(6) with time zone;
ALTER TABLE customer_orders ADD COLUMN handled_by_id bigint;
ALTER TABLE customer_orders ADD COLUMN version bigint DEFAULT 0 NOT NULL;

ALTER TABLE customer_orders
    ADD CONSTRAINT customer_orders_handled_by_fkey
    FOREIGN KEY (handled_by_id) REFERENCES staff_accounts (id);

CREATE INDEX customer_orders_status_created_idx ON customer_orders (status, created_at);
CREATE INDEX customer_orders_short_code_idx ON customer_orders (short_code);

ALTER TABLE order_items ADD COLUMN original_quantity integer;
UPDATE order_items SET original_quantity = quantity;
ALTER TABLE order_items ALTER COLUMN original_quantity SET NOT NULL;

CREATE INDEX order_items_order_id_idx ON order_items (order_id);
