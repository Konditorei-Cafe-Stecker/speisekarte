# Synthetic order history

Café Stecker has no historical sales data yet. To build and test the analysis,
the backend can generate a **reproducible simulated order history** for
1 November 2025 – 31 October 2026.

## What is real and what is simulated

| Part | Real or simulated | Source |
|---|---|---|
| Products, names, prices | **Real** — the imported menu (107 products) | `backend/src/main/resources/db/stecker_menu_import.sql` |
| Hourly weather | **Real** — measured at DWD station *Bremen* (ID 00691) | Deutscher Wetterdienst Open Data via [Bright Sky](https://brightsky.dev) |
| Werder Bremen home matches | **Real** — 17 matches in the Weserstadion with kickoff times and scores | [OpenLigaDB](https://www.openligadb.de) |
| City events | **Real dates** — Freimarkt, Weihnachtsmarkt & Schlachte-Zauber, Osterwiese, Breminale, Freimarktsumzug | `events_bremen.csv`, one source link per row |
| Opening hours | **Assumption** — the hours in `frontend/src/content/site.ts` for the whole year, closed on Bremen public holidays | — |
| The orders themselves | **Simulated** — every order has `data_source = 'SYNTHETIC'` | `SyntheticOrderGenerator` |
| How guests react to weather and events | **Simulated assumptions** | `SyntheticScenario` |

Because the guest reactions are invented, any pattern found in this data is a
**simulation result**. It shows the analysis works; it is not evidence about
the real café. Say so wherever results are shown, e.g. in a CV or portfolio.

Weather data: © Deutscher Wetterdienst (DWD), https://www.dwd.de/opendata.

## Built-in effects

All numbers are in `backend/src/main/java/com/stecker/backend/synthetic/SyntheticScenario.java`.
The analysis should be able to find these again:

| Effect | Assumption |
|---|---|
| Busiest hours | 14:00–16:59, peak 15:00 |
| Weekdays | Monday ×0.80 … Saturday ×1.40, Sunday ×1.20 (but only open 13–18) |
| Tourist season | January ×0.85 … July/August ×1.10 |
| Rain | An hour with ≥ 0.5 mm measured rain: ×0.80 guests |
| Temperature → ice cream | Chance per guest 0.10 × e^(0.08·(T − 15 °C)), max 0.60 |
| Temperature → drinks | Hot drinks fall, cold drinks rise with the hour's temperature |
| Werder home match | 4 h to 30 min before kickoff: ×1.20 guests and ×2.5 alcohol; during the match ×0.85 |
| Weihnachtsmarkt | ×1.30 guests, Glühwein ×4 (Glühwein only Nov–Feb) |
| Freimarkt / Osterwiese / Breminale | ×1.10 guests |
| Freimarktsumzug | ×1.30 guests |
| Strawberries | Products with "Erdbeer" sell ×0.2 outside May–August |
| Product popularity | Fixed shares per subcategory plus staples (Filterkaffee, Cappuccino, …) |
| Cancellations | 3 % of orders; the rest are confirmed. Both happen 1–8 minutes after ordering (`handled_at`, without a staff account) |

With seed `20261120` the run creates **15,770 orders** (15,307 confirmed,
463 cancelled) with 38,274 order lines; the average confirmed order is
about 15 €.

## Tables

| Table | Content |
|---|---|
| `customer_orders`, `order_items` | Orders; filter with `data_source = 'SYNTHETIC'` or `'REAL'` |
| `weather_hourly` | One row per hour, `observed_at` = start of the hour (UTC) |
| `local_events` | Football matches (`type = 'FOOTBALL'`, kickoff → +2 h) and city events (whole local days) |

Order times (`customer_orders.created_at`) are Bremen local time, like
orders from the website. `handled_at`, weather and events are UTC
(`timestamp with time zone`). The views in `analysis/sql/01_views.sql`
provide both; for your own SQL, `created_at AT TIME ZONE 'Europe/Berlin'`
turns the local time into a UTC moment.

Example — ice-cream share by measured temperature:

```sql
select width_bucket(w.temperature_c, -10, 40, 10) as temperature_band,
       round(100.0 * sum(oi.quantity) filter (where m.category = 'Eis & Desserts')
             / sum(oi.quantity), 1) as ice_pct
from order_items oi
join customer_orders o on o.id = oi.order_id
join menu_items m on m.id = oi.menu_item_id
join weather_hourly w
  on w.observed_at = date_trunc('hour', o.created_at at time zone 'Europe/Berlin')
where o.status = 'CONFIRMED' and o.data_source = 'SYNTHETIC'
group by 1 order by 1;
```

When joining orders to `local_events`, several events can overlap
(e.g. Freimarkt and a match day). Aggregate events first, or count orders
with `count(distinct o.id)`, so totals are not counted twice.

## Running the generator

The menu must be imported first (see the README). Flyway creates the
tables. Then, from `backend/` with `STECKER_DB_PASSWORD` set as for the
normal backend:

```bash
./gradlew bootRun --args='--spring.profiles.active=local,synthetic'
```

The app imports the context files, generates the orders and stops.
It prints a summary line starting with `Synthetic orders`.

- **Running again** is refused while synthetic orders exist. To replace them:
  `--args='--spring.profiles.active=local,synthetic --stecker.synthetic.replace=true'`.
  Only `SYNTHETIC` orders are deleted; `REAL` orders are never touched.
- **Same seed → same orders.** Change it with `--stecker.synthetic.seed=…`.
- Other settings: `--stecker.synthetic.start=2025-11-01`, `--stecker.synthetic.end=2026-10-31`.
  The end must be before today and inside the context data.

## Keeping the context data current

The backend updates weather and Werder home matches every day by itself
(see "Daily context update" in the README). The synthetic history still
covers 1 Nov 2025 – 31 Oct 2026; the newer real weather is there for real
orders and for checking forecasts.

## Refreshing the context files

```bash
python3 scripts/fetch_context_data.py 2025-11-01 2026-10-31
```

This rewrites `weather_bremen_hourly.csv` and `football_bremen.csv` in
`backend/src/main/resources/context/`. `events_bremen.csv` is edited by hand;
add a source link for every new row.
