# Analysis

First analysis of the order data: data-quality checks, time patterns,
products, weather and football effects, and an hourly demand model.

| File | Purpose |
|---|---|
| `sql/01_views.sql` | Creates the `analytics` schema with `order_facts` (one row per order, local time, weather of that hour, event flags) and `order_line_facts` (one row per order line with category). Safe to run again. |
| `sql/02_quality_checks.sql` | Data-quality checks; every "problems" count should be 0. |
| `run_analysis.py` | Runs both SQL files, analyses the data and writes `output/report.md` plus charts. |
| `forecast_and_offers.py` | Forecasts orders and product-group demand, tests them on a held-out period, derives offer rules and plans the coming week. Writes `output/forecast_report.md`. |
| `common.py` | Shared code: psql access, opening calendar, event flags, chart style. |

## Run it

Start from the repository root on your Mac, with the synthetic history in
your `stecker` database (see [docs/synthetic-data.md](../docs/synthetic-data.md)).
Read the password privately, as for the backend:

```bash
read -r -s 'STECKER_DB_PASSWORD?Database password: '
```

```bash
export STECKER_DB_PASSWORD
```

```bash
python3 analysis/run_analysis.py
```

Then open `analysis/output/report.md`. The script uses `psql` to read the
database, so no Python database driver is needed; pandas, matplotlib and
statsmodels come with Anaconda.

Options: `--dbname`, `--user`, `--host`, `--port`, `--psql` (path to psql),
`--source SYNTHETIC|REAL|ALL` (default `SYNTHETIC`), `--out`.

You can also use the views directly in pgAdmin, e.g.:

```sql
SELECT local_hour, count(*) AS orders
FROM analytics.order_facts
WHERE status = 'CONFIRMED' AND data_source = 'SYNTHETIC'
GROUP BY local_hour ORDER BY local_hour;
```

## What the report contains

1. Data-quality checks.
2. Headline numbers: orders, revenue, average order value, cancellations.
3. Orders by hour × weekday and by month.
4. Top products (size variants kept apart) and category shares.
5. Ice cream and hot drinks by measured temperature.
6. Alcohol share before Werder home matches.
7. A Poisson regression of orders per opening hour on hour, weekday,
   month, rain and events. Its estimates are compared with the multipliers
   built into the generator, to show the analysis finds them again.

With synthetic data, all results are **simulation results**; the report
says so at the top.

## Forecasts and offers

```bash
python3 analysis/forecast_and_offers.py
```

1. **Forecast test.** Models learn from 1 Nov 2025 – 31 Jul 2026 and are
   scored on 1 Aug – 30 Oct 2026, which they have not seen
   (`--test-start` changes the split). Three models are compared:
   same weekday and hour over the last four weeks; hour + weekday; and
   hour + weekday + weather + events + Werder matches.
2. **Product groups.** Daily units of hot drinks, cold drinks, ice cream,
   alcohol, breakfast and savoury dishes, with the estimated effect of
   a 5 °C warmer day.
3. **Offer rules.** Warm days, cold days, match days, Weihnachtsmarkt,
   quiet mornings and products bought together, each with the numbers
   behind it. No profit effect is claimed: costs and margins are unknown,
   and offers are not yet tracked.
4. **Coming week.** Uses the real DWD forecast for Bremen and upcoming
   Werder home matches (needs internet; skip with `--no-upcoming`).
5. **Publishing.** `--publish` also writes today + 6 days into the
   forecast tables that the admin dashboard (`/admin`) reads: expected and
   likely-range orders and revenue per day, orders per hour, and expected
   units per product (product-group forecast × the product's share around
   the same time last year).
