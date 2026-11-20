#!/usr/bin/env python3
"""First analysis of Café Stecker orders.

Reads the database through `psql` (no Python database driver needed),
checks data quality, and writes a Markdown report with charts to
analysis/output/.

    # your local database (password read from STECKER_DB_PASSWORD)
    python3 analysis/run_analysis.py

    # options
    python3 analysis/run_analysis.py --dbname stecker --user stecker_app --source SYNTHETIC

Needs pandas, matplotlib and statsmodels (all included in Anaconda).
"""

import argparse
import sys
from datetime import datetime
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np
import pandas as pd
import statsmodels.api as sm
import statsmodels.formula.api as smf

from common import (BLUE, BLUE_RAMP, GRID, HERE, MONTHS, ORANGE, SQL_DIR,
                    SUNDAY_OPEN, SURFACE, TEXT_SECONDARY, WEEKDAYS, Database,
                    add_database_arguments, add_event_flags, euro, load_events,
                    load_weather, md, open_hours, save, style)

# The multipliers built into the generator, copied from
# backend/src/main/java/com/stecker/backend/synthetic/SyntheticScenario.java.
# Only used to check whether the analysis finds them again.
BUILT_IN = {
    "Rainy hour (≥ 0.5 mm)": ("rainy", 0.80),
    "Christmas market": ("christmas_market", 1.30),
    "Fair (Freimarkt, Osterwiese)": ("fair", 1.10),
    "Festival (Breminale)": ("festival", 1.10),
    "Parade (Freimarktsumzug)": ("parade", 1.30),
    "Before a Werder home match": ("pre_match", 1.20),
    "During a Werder home match": ("during_match", 0.85),
}
WEEKDAY_FACTOR = [0.80, 0.85, 0.90, 0.95, 1.10, 1.40, 1.20]


# ---------------------------------------------------------------- analysis

def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    add_database_arguments(parser)
    parser.add_argument("--source", default="SYNTHETIC", choices=["SYNTHETIC", "REAL", "ALL"])
    parser.add_argument("--out", default=str(HERE / "output"))
    args = parser.parse_args()

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    db = Database(args)

    print("Creating analysis views …", file=sys.stderr)
    db.run_file(SQL_DIR / "01_views.sql")

    # ---- 1. Data quality
    checks = db.query((SQL_DIR / "02_quality_checks.sql").read_text())
    blocking = checks[~checks["check_name"].str.startswith("information")
                      & (checks["problems"] > 0)]

    source_filter = "" if args.source == "ALL" else f"AND data_source = '{args.source}'"
    all_orders = db.query(f"""
        SELECT order_id, status, data_source FROM analytics.order_facts
        WHERE true {source_filter}""")
    orders = db.query(f"""
        SELECT * FROM analytics.order_facts
        WHERE status = 'CONFIRMED' {source_filter}""")
    lines = db.query(f"""
        SELECT order_id, local_date, local_hour, iso_weekday, month, temperature_c,
               pre_match, match_day, category, subcategory, product_label,
               quantity, line_total
        FROM analytics.order_line_facts
        WHERE status = 'CONFIRMED' {source_filter}""")

    if orders.empty:
        hint = ("Generate them first (see docs/synthetic-data.md)."
                if args.source != "REAL" else "No guest has placed and confirmed an order yet.")
        sys.exit(f"No confirmed {args.source} orders found. {hint}")

    orders["local_time"] = pd.to_datetime(orders["local_time"])
    orders["local_date"] = pd.to_datetime(orders["local_date"]).dt.date
    first, last = min(orders["local_date"]), max(orders["local_date"])

    hours = open_hours(first, last)
    open_days = hours["local_date"].nunique()

    # ---- 2. Headline numbers
    revenue = orders["total_price"].sum()
    kpis = pd.DataFrame([
        ("Period", f"{first} – {last}"),
        ("Open days", f"{open_days}"),
        ("Confirmed orders", f"{len(orders):,}"),
        ("Cancelled orders",
         f"{(all_orders['status'] == 'CANCELLED').sum()} "
         f"({(all_orders['status'] == 'CANCELLED').mean():.1%})"),
        ("Orders per open day", f"{len(orders) / open_days:.1f}"),
        ("Confirmed revenue", euro(revenue)),
        ("Revenue per open day", euro(revenue / open_days)),
        ("Average order value", euro(orders["total_price"].mean())),
        ("Median order value", euro(orders["total_price"].median())),
        ("Items per order", f"{orders['items'].mean():.2f}"),
    ], columns=["Measure", "Value"])

    # ---- 3. Time of day × weekday
    orders["weekday"] = orders["iso_weekday"] - 1
    days_per_weekday = hours.groupby("weekday")["local_date"].nunique()
    heat = (orders.groupby(["weekday", "local_hour"]).size()
            .unstack(fill_value=0)
            .div(days_per_weekday, axis=0)
            .reindex(columns=range(9, 18), fill_value=0))
    heat.loc[6, [h for h in heat.columns if h < SUNDAY_OPEN]] = np.nan  # Sunday mornings: closed
    fig, ax = plt.subplots(figsize=(8, 3.8))
    cmap = matplotlib.colors.LinearSegmentedColormap.from_list("blues", BLUE_RAMP)
    cmap.set_bad(SURFACE)
    image = ax.imshow(np.ma.masked_invalid(heat.values.astype(float)), cmap=cmap,
                      aspect="auto", vmin=0)
    closed = [i for i, h in enumerate(heat.columns) if h < SUNDAY_OPEN]
    ax.text(np.mean(closed), 6, "closed", ha="center", va="center",
            color=TEXT_SECONDARY, fontsize=9)
    ax.set_xticks(range(len(heat.columns)), [f"{h}:00" for h in heat.columns])
    ax.set_yticks(range(7), WEEKDAYS)
    style(ax, "Orders per hour, average open day", "Hour the order was placed")
    ax.grid(False)
    bar = fig.colorbar(image, ax=ax, fraction=0.04, pad=0.02)
    bar.outline.set_visible(False)
    bar.ax.tick_params(colors=TEXT_SECONDARY, length=0)
    chart_heat = save(fig, out, "orders_by_hour_weekday.png")

    by_weekday = pd.DataFrame({
        "Weekday": WEEKDAYS,
        "Open days": days_per_weekday.values,
        "Orders per day": (orders.groupby("weekday").size() / days_per_weekday).round(1).values,
        "Revenue per day": (orders.groupby("weekday")["total_price"].sum()
                            / days_per_weekday).map(euro).values,
        "Avg order": orders.groupby("weekday")["total_price"].mean().map(euro).values,
    })

    # ---- 4. Months
    days_per_month = hours.groupby("month")["local_date"].nunique()
    monthly = orders.groupby("month").agg(orders=("order_id", "size"),
                                          revenue=("total_price", "sum"))
    month_order = [((first.month - 1 + i) % 12) + 1 for i in range(12)]
    monthly = monthly.reindex([m for m in month_order if m in monthly.index])
    monthly["per_day"] = monthly["orders"] / days_per_month.reindex(monthly.index)
    fig, ax = plt.subplots(figsize=(8, 3.6))
    labels = [MONTHS[m - 1] for m in monthly.index]
    ax.bar(labels, monthly["per_day"], color=BLUE, width=0.7, edgecolor=SURFACE, linewidth=2)
    style(ax, "Orders per open day, by month", ylabel="Orders")
    chart_month = save(fig, out, "orders_by_month.png")
    monthly_table = pd.DataFrame({
        "Month": labels,
        "Orders": monthly["orders"].values,
        "Orders per open day": monthly["per_day"].round(1).values,
        "Revenue": monthly["revenue"].map(euro).values,
    })

    # ---- 5. Products
    products = (lines.groupby(["product_label", "category"])
                .agg(sold=("quantity", "sum"), revenue=("line_total", "sum"))
                .reset_index())
    top_qty = products.sort_values("sold", ascending=False).head(15)
    fig, ax = plt.subplots(figsize=(8, 5.2))
    ax.barh(top_qty["product_label"][::-1], top_qty["sold"][::-1], color=BLUE,
            height=0.7, edgecolor=SURFACE, linewidth=2)
    style(ax, "Top 15 products by quantity sold", xlabel="Units")
    ax.grid(axis="y", visible=False)
    ax.grid(axis="x", color=GRID, linewidth=0.8)
    chart_products = save(fig, out, "top_products.png")

    top_revenue = products.sort_values("revenue", ascending=False).head(10)
    top_revenue_table = pd.DataFrame({
        "Product": top_revenue["product_label"],
        "Category": top_revenue["category"],
        "Units": top_revenue["sold"],
        "Revenue": top_revenue["revenue"].map(euro),
    })
    categories = (lines.groupby("category")
                  .agg(units=("quantity", "sum"), revenue=("line_total", "sum"))
                  .reset_index().sort_values("revenue", ascending=False))
    categories["Revenue share"] = (categories["revenue"] / categories["revenue"].sum()).map("{:.1%}".format)
    categories["revenue"] = categories["revenue"].map(euro)
    categories.columns = ["Category", "Units", "Revenue", "Revenue share"]

    # ---- 6. Weather
    bands = [-np.inf, 5, 10, 15, 20, 25, np.inf]
    band_names = ["under 5 °C", "5–10 °C", "10–15 °C", "15–20 °C", "20–25 °C", "25 °C +"]
    lines["band"] = pd.cut(lines["temperature_c"], bands, labels=band_names, right=False)
    lines["ice"] = lines["category"].eq("Eis & Desserts") * lines["quantity"]
    hot = {"Azul Kaffee", "Schokolade", "Zum Aufwärmen", "Tee (Glas)",
           "Tee auf Stövchen traditionell"}
    lines["hot"] = lines["subcategory"].isin(hot) * lines["quantity"]
    weather = lines.groupby("band", observed=False).agg(
        units=("quantity", "sum"), ice=("ice", "sum"), hot=("hot", "sum"))
    weather["ice_share"] = weather["ice"] / weather["units"]
    weather["hot_share"] = weather["hot"] / weather["units"]

    fig, ax = plt.subplots(figsize=(8, 3.6))
    ax.bar(band_names, weather["ice_share"] * 100, color=BLUE, width=0.7,
           edgecolor=SURFACE, linewidth=2)
    for x, value in enumerate(weather["ice_share"] * 100):
        ax.text(x, value + 0.6, f"{value:.1f} %", ha="center", color=TEXT_SECONDARY, fontsize=9)
    style(ax, "Share of items that are ice cream or desserts",
          xlabel="Measured temperature in that hour (DWD Bremen)", ylabel="% of items")
    chart_ice = save(fig, out, "ice_share_by_temperature.png")
    weather_table = pd.DataFrame({
        "Temperature": band_names,
        "Items sold": weather["units"].values,
        "Ice cream & desserts": weather["ice_share"].map("{:.1%}".format).values,
        "Hot drinks": weather["hot_share"].map("{:.1%}".format).values,
    })

    # ---- 7. Hourly demand model (Poisson regression)
    weather_hours = load_weather(db)
    events = load_events(db)

    counts = (orders.assign(hour_start_utc=pd.to_datetime(orders["hour_start_utc"], utc=True))
              .groupby("hour_start_utc").size().rename("orders"))
    model_data = (hours.merge(weather_hours, left_on="hour_start_utc",
                              right_on="observed_at", how="left")
                  .merge(counts, left_on="hour_start_utc", right_index=True, how="left"))
    model_data["orders"] = model_data["orders"].fillna(0).astype(int)
    model_data["rainy"] = (model_data["precipitation_mm"] >= 0.5).astype(int)
    model_data = add_event_flags(model_data, events)

    formula = ("orders ~ C(hour) + C(weekday) + C(month) + rainy + christmas_market"
               " + fair + festival + parade + pre_match + during_match")
    model = smf.glm(formula, data=model_data, family=sm.families.Poisson(),
                    offset=np.log(model_data["minutes_open"] / 60)).fit()
    dispersion = model.pearson_chi2 / model.df_resid

    intervals = model.conf_int()
    recovery_rows = []
    for label, (term, truth) in BUILT_IN.items():
        estimate = np.exp(model.params[term])
        low, high = np.exp(intervals.loc[term])
        hours_flagged = int(model_data[term].sum())
        recovery_rows.append((label, truth, estimate, low, high, hours_flagged,
                              low <= truth <= high))
    for i, name in enumerate(WEEKDAYS[1:], start=1):
        term = f"C(weekday)[T.{i}]"
        truth = WEEKDAY_FACTOR[i] / WEEKDAY_FACTOR[0]
        estimate = np.exp(model.params[term])
        low, high = np.exp(intervals.loc[term])
        recovery_rows.append((f"{name} vs Monday", truth, estimate, low, high,
                              int((model_data["weekday"] == i).sum()), low <= truth <= high))
    recovery = pd.DataFrame(recovery_rows, columns=[
        "Effect", "Built in", "Estimated", "95 % low", "95 % high", "Hours", "Recovered"])

    fig, ax = plt.subplots(figsize=(8, 5.6))
    y = np.arange(len(recovery))[::-1]
    ax.hlines(y, recovery["95 % low"], recovery["95 % high"], color=BLUE, linewidth=2)
    ax.plot(recovery["Estimated"], y, "o", color=BLUE, markersize=8,
            markeredgecolor=SURFACE, markeredgewidth=2, label="Estimated, with 95 % interval",
            zorder=3)
    ax.plot(recovery["Built in"], y, "|", color=ORANGE, markersize=18,
            markeredgewidth=2.5, label="Built into the simulation", zorder=4)
    ax.axvline(1, color=TEXT_SECONDARY, linewidth=0.8, linestyle=(0, (3, 3)))
    ax.set_yticks(y, recovery["Effect"])
    style(ax, "Does the analysis find the simulated effects?",
          xlabel="Multiplier on orders per hour (1 = no effect)")
    ax.grid(axis="y", visible=False)
    ax.grid(axis="x", color=GRID, linewidth=0.8)
    ax.legend(frameon=False, loc="upper left", bbox_to_anchor=(0, -0.13), ncol=2,
              labelcolor=TEXT_SECONDARY)
    chart_recovery = save(fig, out, "effect_recovery.png")

    # ---- 8. Football: alcohol before matches
    lines["weekday"] = lines["iso_weekday"] - 1
    lines["alcohol"] = lines["subcategory"].isin(
        {"Biere", "Weine", "Sekt & Prosecco", "Longdrinks"}) * lines["quantity"]
    saturday_late_morning = lines[(lines["weekday"] == 5) & lines["local_hour"].between(11, 14)]
    football = (saturday_late_morning.groupby("pre_match")
                .agg(items=("quantity", "sum"), alcohol=("alcohol", "sum")))
    football_table = pd.DataFrame({
        "Saturday 11:00–14:59": ["Before a Werder home match" if flag else "No match"
                                 for flag in football.index],
        "Items": football["items"].values,
        "Alcohol share": (football["alcohol"] / football["items"]).map("{:.1%}".format).values,
    })

    # ---- 9. Report
    recovered = int(recovery["Recovered"].sum())
    show = recovery.copy()
    for column in ["Built in", "Estimated", "95 % low", "95 % high"]:
        show[column] = show[column].map("{:.2f}".format)
    show["Recovered"] = show["Recovered"].map({True: "yes", False: "no"})

    synthetic = args.source == "SYNTHETIC"
    notice = (
        "> **Simulation results.** These orders are synthetic (`data_source = 'SYNTHETIC'`).\n"
        "> The menu, prices, weather, football matches and event dates are real; how guests\n"
        "> react to them is an assumption built into the generator. The patterns below show\n"
        "> that the analysis works. They are not evidence about the real café.\n"
        if synthetic else
        f"> Data source: {args.source}.\n")

    quality_lines = "\n".join(
        f"- {'✅' if row.problems == 0 or row.check_name.startswith('information') else '❌'} "
        f"{row.check_name}: **{row.problems}**"
        for row in checks.itertuples())

    report = f"""# Café Stecker — first order analysis

Generated {datetime.now():%Y-%m-%d %H:%M} from database `{args.dbname}`, source `{args.source}`.

{notice}
## Data quality

{quality_lines}

{"All blocking checks passed." if blocking.empty else "**Some checks failed — fix the data before trusting the numbers below.**"}

## Headline numbers (confirmed orders)

{md(kpis)}

## When guests order

![Orders per hour and weekday]({chart_heat})

The 17:00 column only covers 17:00–17:29 (last orders before closing at 18:00).

{md(by_weekday)}

![Orders per open day by month]({chart_month})

{md(monthly_table)}

## What guests order

![Top products]({chart_products})

Top products by revenue:

{md(top_revenue_table)}

{md(categories)}

Size variants are listed separately (for example both Apollinaris sizes),
because several products share a name.

## Weather

Each order is matched to the DWD measurement of the hour it was placed in.

![Ice-cream share by temperature]({chart_ice})

{md(weather_table)}

## Football

{md(football_table)}

## Hourly demand model

A Poisson regression on all {len(model_data):,} opening hours explains the number
of orders per hour by hour of day, weekday, month, rain and events
(offset for the shortened last hour). Overdispersion: {dispersion:.2f}
(about 1 means the Poisson model fits the spread of the counts).

![Effect recovery]({chart_recovery})

{md(show)}

**{recovered} of {len(recovery)}** built-in effects lie inside the 95 % interval.
Effects with few hours (Breminale: 5 days, Freimarktsumzug: 1 day) have wide
intervals; a single year cannot measure them precisely.
"""
    report_path = out / "report.md"
    report_path.write_text(report, encoding="utf-8")
    print(f"Wrote {report_path}", file=sys.stderr)
    print(f"Quality: {'OK' if blocking.empty else 'PROBLEMS'}; "
          f"{len(orders)} confirmed orders; recovered {recovered}/{len(recovery)} effects",
          file=sys.stderr)


if __name__ == "__main__":
    main()
