# Analysis

First analysis of the order data: data-quality checks, time patterns,
products, weather and football effects, and an hourly demand model.

| File | Purpose |
|---|---|
| `sql/01_views.sql` | Creates the `analytics` schema with `order_facts` (one row per order, local time, weather of that hour, event flags) and `order_line_facts` (one row per order line with category). Safe to run again. |
| `sql/02_quality_checks.sql` | Data-quality checks; every "problems" count should be 0. |
| `run_analysis.py` | Runs both SQL files, analyses the data and writes `output/report.md` plus charts. |

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
