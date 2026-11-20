#!/usr/bin/env python3
"""Demand forecasts and explainable offer suggestions for Café Stecker.

1. Forecasts orders per opening hour and units per product group, and
   tests them on a held-out later period (no information from the test
   period is used for training).
2. Derives offer rules from the data; each rule states the numbers behind it.
3. Optionally plans the coming week with the real DWD weather forecast for
   Bremen, real upcoming Werder Bremen home matches and the city events.

    python3 analysis/forecast_and_offers.py               # your stecker database
    python3 analysis/forecast_and_offers.py --no-upcoming # without internet access

Writes analysis/output/forecast_report.md with charts.
"""

import argparse
import json
import sys
import urllib.request
from datetime import date, datetime, timedelta
from itertools import combinations
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402
import pandas as pd  # noqa: E402
import statsmodels.api as sm  # noqa: E402
import statsmodels.formula.api as smf  # noqa: E402

from common import (BERLIN, BLUE, GRID, HERE, ORANGE, AQUA, SQL_DIR, SURFACE,  # noqa: E402
                    TEXT_SECONDARY, WEEKDAYS, Database, add_database_arguments,
                    add_event_flags, load_events, load_weather, md, open_hours,
                    product_group, save, style)

STATION = "00691"
USER_AGENT = "speisekarte-forecast/1.0"
EVENT_COLUMNS = ["christmas_market", "fair", "festival", "parade", "pre_match", "during_match"]

HOURLY_MODELS = {
    "Hour + weekday": "orders ~ C(hour) + C(weekday)",
    "Hour + weekday + weather + events":
        "orders ~ C(hour) + C(weekday) + temperature_c + rainy + "
        + " + ".join(EVENT_COLUMNS),
}
DAILY_FORMULA = ("units ~ C(weekday) + temp_mean + rainy_hours + christmas_market"
                 " + fair + festival + parade + pre_match_hours")


# ---------------------------------------------------------------- data

def hourly_frame(hours, weather, events, order_hours):
    """One row per opening hour: features and the number of orders."""
    frame = hours.merge(weather, left_on="hour_start_utc", right_on="observed_at", how="left")
    frame["rainy"] = (frame["precipitation_mm"] >= 0.5).astype(int)
    frame = add_event_flags(frame, events)
    frame["log_open"] = np.log(frame["minutes_open"] / 60)
    if order_hours is not None:
        frame = frame.merge(order_hours, left_on="hour_start_utc", right_index=True, how="left")
        frame["orders"] = frame["orders"].fillna(0).astype(int)
    return frame


def daily_frame(hourly):
    """Aggregates opening hours to days (features for product-group models)."""
    daily = hourly.groupby("local_date").agg(
        weekday=("weekday", "first"),
        open_hours=("minutes_open", lambda m: m.sum() / 60),
        temp_mean=("temperature_c", "mean"),
        temp_max=("temperature_c", "max"),
        rainy_hours=("rainy", "sum"),
        christmas_market=("christmas_market", "max"),
        fair=("fair", "max"),
        festival=("festival", "max"),
        parade=("parade", "max"),
        pre_match_hours=("pre_match", "sum"),
    ).reset_index()
    daily["log_open"] = np.log(daily["open_hours"])
    return daily


def fit_poisson(formula, data, offset_column="log_open"):
    return smf.glm(formula, data=data, family=sm.families.Poisson(),
                   offset=data[offset_column]).fit()


def predict(model, data, offset_column="log_open"):
    return model.predict(data, offset=data[offset_column])


def same_slot_baseline(frame, weeks=4):
    """Average of the same weekday and hour over the previous `weeks` weeks.
    Uses only days before the forecast day (a one-day-ahead forecast)."""
    history = frame.set_index(["local_date", "hour"])["orders"]
    result = []
    for row in frame.itertuples():
        values = [history.get((row.local_date - timedelta(weeks=w), row.hour))
                  for w in range(1, weeks + 1)]
        values = [v for v in values if v is not None and not np.isnan(v)]
        result.append(np.mean(values) if values else np.nan)
    return np.array(result)


def errors(actual, forecast):
    actual = np.asarray(actual, dtype=float)
    forecast = np.asarray(forecast, dtype=float)
    mask = ~np.isnan(forecast)
    diff = forecast[mask] - actual[mask]
    return {
        "MAE": np.mean(np.abs(diff)),
        "RMSE": np.sqrt(np.mean(diff ** 2)),
        "Bias": np.mean(diff),
        "MAPE": np.mean(np.abs(diff) / np.maximum(actual[mask], 1)),
    }


# ---------------------------------------------------------------- upcoming week

def get_json(url):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def fetch_upcoming(first, last):
    """Real DWD forecast (MOSMIX via Bright Sky) and Werder home fixtures."""
    weather = get_json(
        "https://api.brightsky.dev/weather"
        f"?dwd_station_id={STATION}"
        f"&date={first.isoformat()}T00:00:00%2B00:00"
        f"&last_date={(last + timedelta(days=2)).isoformat()}T00:00:00%2B00:00"
        "&tz=Etc/UTC")
    forecast = pd.DataFrame([{
        "observed_at": pd.Timestamp(h["timestamp"]).tz_convert("UTC"),
        "temperature_c": h["temperature"],
        "precipitation_mm": h["precipitation"] or 0.0,
    } for h in weather["weather"]])
    observation_types = sorted({s["observation_type"] for s in weather["sources"]})

    matches = []
    for league in ("bl1", "dfb"):
        for season in (first.year - 1, first.year):
            try:
                data = get_json(f"https://api.openligadb.de/getmatchdata/{league}/{season}")
            except Exception:  # season not published
                continue
            for match in data:
                if "Werder" not in match["team1"]["teamName"]:
                    continue
                kickoff = pd.Timestamp(match["matchDateTimeUTC"]).tz_convert("UTC")
                local_day = kickoff.tz_convert(BERLIN).date()
                if first <= local_day <= last:
                    matches.append({
                        "type": "FOOTBALL",
                        "name": f"{match['team1']['teamName']} – {match['team2']['teamName']}",
                        "starts_at": kickoff,
                        "ends_at": kickoff + pd.Timedelta(hours=2),
                    })
    return forecast, pd.DataFrame(matches), observation_types


# ---------------------------------------------------------------- main

def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    add_database_arguments(parser)
    parser.add_argument("--source", default="SYNTHETIC", choices=["SYNTHETIC", "REAL", "ALL"])
    parser.add_argument("--test-start", default="2026-08-01",
                        help="first day of the held-out test period")
    parser.add_argument("--no-upcoming", action="store_true",
                        help="skip planning the coming week (needs internet)")
    parser.add_argument("--out", default=str(HERE / "output"))
    args = parser.parse_args()

    db = Database(args)
    db.run_file(SQL_DIR / "01_views.sql")
    source_filter = "" if args.source == "ALL" else f"AND data_source = '{args.source}'"

    orders = db.query(f"""
        SELECT order_id, hour_start_utc, local_date, local_hour, iso_weekday
        FROM analytics.order_facts WHERE status = 'CONFIRMED' {source_filter}""")
    lines = db.query(f"""
        SELECT order_id, local_date, local_hour, iso_weekday, temperature_c,
               pre_match, christmas_market, category, subcategory, product_label, quantity
        FROM analytics.order_line_facts WHERE status = 'CONFIRMED' {source_filter}""")
    if orders.empty:
        sys.exit(f"No confirmed {args.source} orders found.")

    weather = load_weather(db)
    events = load_events(db)
    orders["local_date"] = pd.to_datetime(orders["local_date"]).dt.date
    lines["local_date"] = pd.to_datetime(lines["local_date"]).dt.date
    lines["group"] = [product_group(c, s) for c, s in zip(lines["category"], lines["subcategory"])]

    first, last = min(orders["local_date"]), max(orders["local_date"])
    test_start = date.fromisoformat(args.test_start)
    order_hours = (orders.assign(hour_start_utc=pd.to_datetime(orders["hour_start_utc"], utc=True))
                   .groupby("hour_start_utc").size().rename("orders"))
    hourly = hourly_frame(open_hours(first, last), weather, events, order_hours)
    train = hourly[hourly["local_date"] < test_start].copy()
    test = hourly[hourly["local_date"] >= test_start].copy()

    # ---- 1. Hourly order forecasts on the held-out period
    test["Same slot, last 4 weeks"] = same_slot_baseline(hourly)[hourly["local_date"] >= test_start]
    fitted = {}
    for name, formula in HOURLY_MODELS.items():
        fitted[name] = fit_poisson(formula, train)
        test[name] = predict(fitted[name], test)
    model_names = ["Same slot, last 4 weeks"] + list(HOURLY_MODELS)

    def daily_totals(frame):
        return frame.groupby("local_date")[["orders"] + model_names].sum(min_count=1)

    test_days = daily_totals(test)
    special = test.groupby("local_date")[EVENT_COLUMNS].max().any(axis=1)
    rows = []
    for name in model_names:
        hourly_error = errors(test["orders"], test[name])
        daily_error = errors(test_days["orders"], test_days[name])
        special_error = errors(test_days.loc[special, "orders"], test_days.loc[special, name])
        rows.append({
            "Model": name,
            "MAE per hour": f"{hourly_error['MAE']:.2f}",
            "MAE per day": f"{daily_error['MAE']:.1f}",
            "MAPE per day": f"{daily_error['MAPE']:.1%}",
            "Bias per day": f"{daily_error['Bias']:+.1f}",
            "MAE on event & match days": f"{special_error['MAE']:.1f}",
        })
    hourly_scores = pd.DataFrame(rows)
    best = "Hour + weekday + weather + events"

    out = args.out
    fig, ax = plt.subplots(figsize=(9, 3.8))
    x = pd.to_datetime(test_days.index)
    ax.plot(x, test_days["orders"], color=BLUE, linewidth=2, label="Actual orders")
    ax.plot(x, test_days[best], color=ORANGE, linewidth=2, label="Forecast (weather + events)")
    ax.plot(x, test_days["Same slot, last 4 weeks"], color=AQUA, linewidth=2,
            linestyle=(0, (4, 3)), label="Same slot, last 4 weeks")
    style(ax, f"Orders per day in the test period ({test_start:%d %b} – {last:%d %b %Y})",
          ylabel="Orders")
    ax.legend(frameon=False, loc="upper left", bbox_to_anchor=(0, -0.1), ncol=3,
              labelcolor=TEXT_SECONDARY)
    chart_test = save(fig, out, "forecast_test_period.png")

    # ---- 2. Product-group forecasts (units per day)
    daily = daily_frame(hourly)
    units = (lines.groupby(["local_date", "group"])["quantity"].sum()
             .unstack(fill_value=0))
    groups = ["Hot drinks", "Cold drinks", "Ice cream & desserts", "Alcohol", "Breakfast", "Savoury"]
    group_rows = []
    group_models = {}
    for group in groups:
        data = daily.merge(units[group].rename("units"), left_on="local_date",
                           right_index=True, how="left").fillna({"units": 0})
        group_train = data[data["local_date"] < test_start]
        group_test = data[data["local_date"] >= test_start]
        baseline = group_train.groupby("weekday")["units"].mean()
        model = fit_poisson(DAILY_FORMULA, group_train)
        group_models[group] = fit_poisson(DAILY_FORMULA, data)  # refit on everything for planning
        base_error = errors(group_test["units"], group_test["weekday"].map(baseline))
        model_error = errors(group_test["units"], predict(model, group_test))
        per_5_degrees = np.exp(5 * model.params["temp_mean"]) - 1
        group_rows.append({
            "Product group": group,
            "Avg units/day (test)": f"{group_test['units'].mean():.1f}",
            "MAE weekday average": f"{base_error['MAE']:.1f}",
            "MAE with weather & events": f"{model_error['MAE']:.1f}",
            "Improvement": f"{1 - model_error['MAE'] / base_error['MAE']:.0%}",
            "Change per +5 °C": f"{per_5_degrees:+.0%}",
        })
    group_scores = pd.DataFrame(group_rows)

    # ---- 3. Offer rules, each backed by numbers from the data
    lines["weekday"] = lines["iso_weekday"] - 1
    total_units = lines["quantity"].sum()

    def share(frame, group):
        return frame.loc[frame["group"] == group, "quantity"].sum() / max(frame["quantity"].sum(), 1)

    def top_product(frame, group):
        subset = frame[frame["group"] == group]
        return subset.groupby("product_label")["quantity"].sum().idxmax()

    warm = lines[lines["temperature_c"] >= 22]
    cold = lines[lines["temperature_c"] < 5]
    saturday_late_morning = lines[(lines["weekday"] == 5) & lines["local_hour"].between(11, 14)]
    pre_match = saturday_late_morning[saturday_late_morning["pre_match"]]
    no_match = saturday_late_morning[~saturday_late_morning["pre_match"]]
    winter = lines[pd.to_datetime(lines["local_date"]).dt.month.isin([11, 12, 1, 2])]
    gluehwein = lambda f: f.loc[f["product_label"].str.contains("Glühwein"), "quantity"].sum() / max(f["quantity"].sum(), 1)  # noqa: E731

    # Quiet slots: average orders per hour by weekday × hour, from the hour + weekday model
    slot_profile = (hourly.assign(rate=predict(fitted["Hour + weekday"], hourly) /
                                  (hourly["minutes_open"] / 60))
                    .groupby(["weekday", "hour"])["rate"].mean())
    peak = slot_profile.max()
    quiet = slot_profile[(slot_profile < 0.45 * peak)]
    quiet_morning = quiet[[h < 12 for _, h in quiet.index]]
    morning_lines = lines[(lines["local_hour"] < 11) & (lines["weekday"] < 5)]
    quiet_weekdays = sorted({d for d, h in quiet_morning.index
                             if all((d, hh) in quiet.index for hh in (9, 10, 11))})
    quiet_days = (f"{WEEKDAYS[quiet_weekdays[0]]}–{WEEKDAYS[quiet_weekdays[-1]]}"
                  if len(quiet_weekdays) > 2 and quiet_weekdays == list(
                      range(quiet_weekdays[0], quiet_weekdays[-1] + 1))
                  else ", ".join(WEEKDAYS[d] for d in quiet_weekdays) or "weekdays")

    # Basket analysis: which product groups are bought together (lift)
    order_groups = lines.groupby("order_id")["group"].apply(set)
    n_orders = len(order_groups)
    group_counts = {g: sum(g in s for s in order_groups) for g in groups + ["Bar snacks"]}
    pairs = []
    for a, b in combinations(groups + ["Bar snacks"], 2):
        both = sum(a in s and b in s for s in order_groups)
        if both == 0:
            continue
        support = both / n_orders
        lift = support / ((group_counts[a] / n_orders) * (group_counts[b] / n_orders))
        pairs.append({"Pair": f"{a} + {b}", "Orders with both": both,
                      "Support": support, "Lift": lift})
    basket = pd.DataFrame(pairs).sort_values("Lift", ascending=False)
    strong_pairs = basket[(basket["Lift"] >= 1.2) & (basket["Support"] >= 0.01)]
    basket_show = basket.head(6).copy()
    basket_show["Support"] = basket_show["Support"].map("{:.1%}".format)
    basket_show["Lift"] = basket_show["Lift"].map("{:.2f}".format)

    rules = [
        {
            "key": "warm",
            "Trigger": "Forecast ≥ 22 °C during opening hours",
            "Suggestion": f"Feature ice cream, e.g. {top_product(warm, 'Ice cream & desserts')}",
            "Evidence": f"Ice cream & desserts are {share(warm, 'Ice cream & desserts'):.0%} of items "
                        f"at ≥ 22 °C vs {share(lines, 'Ice cream & desserts'):.0%} overall",
        },
        {
            "key": "cold",
            "Trigger": "Forecast below 5 °C",
            "Suggestion": "Hot-drink offer, e.g. Tasse Schokolade or a pot of tea",
            "Evidence": f"Hot drinks are {share(cold, 'Hot drinks'):.0%} of items below 5 °C "
                        f"vs {share(lines, 'Hot drinks'):.0%} overall",
        },
        {
            "key": "match",
            "Trigger": "Werder home match: 4 h to 30 min before kickoff",
            "Suggestion": f"Match-day bundle: {top_product(pre_match, 'Alcohol')} "
                          f"+ {top_product(lines, 'Bar snacks')}",
            "Evidence": f"Alcohol is {share(pre_match, 'Alcohol'):.1%} of items before matches "
                        f"vs {share(no_match, 'Alcohol'):.1%} at the same time on other Saturdays",
        },
        {
            "key": "christmas",
            "Trigger": "Weihnachtsmarkt is running",
            "Suggestion": "Glühwein offer near the window; expect more guests",
            "Evidence": f"Glühwein is {gluehwein(lines[lines['christmas_market']]):.1%} of items "
                        f"during the market vs {gluehwein(winter[~winter['christmas_market']]):.1%} "
                        "in other winter weeks",
        },
        {
            "key": "quiet",
            "Trigger": f"Quiet mornings 9:00–11:59 on {quiet_days}",
            "Suggestion": f"Breakfast bundle: {top_product(morning_lines, 'Breakfast')} "
                          f"+ {top_product(morning_lines, 'Hot drinks')}",
            "Evidence": f"These hours average {quiet.loc[[(d, h) for d in quiet_weekdays for h in (9, 10, 11)]].mean():.1f} orders "
                        f"per hour vs {peak:.1f} in the busiest hour of the week",
        },
    ]
    if not strong_pairs.empty:
        pair = strong_pairs.iloc[0]
        rules.append({
            "key": "basket",
            "Trigger": "Any time",
            "Suggestion": f"Bundle {pair['Pair']}",
            "Evidence": f"Bought together {pair['Lift']:.2f}× as often as chance "
                        f"({pair['Support']:.1%} of orders)",
        })
    rules_table = pd.DataFrame(rules).drop(columns="key")

    # ---- 4. The coming week
    upcoming_section = "_Skipped (`--no-upcoming`)._"
    chart_week = None
    if not args.no_upcoming:
        try:
            tomorrow = datetime.now().astimezone().date() + timedelta(days=1)
            week_last = tomorrow + timedelta(days=6)
            forecast, matches, observation_types = fetch_upcoming(tomorrow, week_last)
            future_events = pd.concat([events[events["type"] != "FOOTBALL"], matches],
                                      ignore_index=True)
            future_hours = hourly_frame(open_hours(tomorrow, week_last), forecast,
                                        future_events, None)
            full_model = fit_poisson(HOURLY_MODELS[best], hourly)
            future_hours["forecast"] = predict(full_model, future_hours)
            future_daily = daily_frame(future_hours)
            plan_rows = []
            for day in future_daily.itertuples():
                day_hours = future_hours[future_hours["local_date"] == day.local_date]
                expected = day_hours["forecast"].sum()
                peak_hour = day_hours.loc[day_hours["forecast"].idxmax(), "hour"]
                group_text = []
                for group in ("Hot drinks", "Ice cream & desserts", "Alcohol"):
                    frame = pd.DataFrame([day._asdict()])
                    group_text.append(f"{group.split(' ')[0]} {predict(group_models[group], frame).iloc[0]:.0f}")
                offers = []
                if day.temp_max >= 22:
                    offers.append("ice cream")
                if day.temp_mean < 5:
                    offers.append("hot drinks")
                if day.pre_match_hours > 0:
                    match = matches[matches["starts_at"].dt.tz_convert(BERLIN).dt.date
                                    == day.local_date].iloc[0]
                    offers.append(f"match bundle before {match['starts_at'].tz_convert(BERLIN):%H:%M} "
                                  f"({match['name'].split(' – ')[1]})")
                if day.christmas_market:
                    offers.append("Glühwein (Weihnachtsmarkt)")
                if day.weekday in quiet_weekdays:
                    offers.append("breakfast bundle 9–11")
                plan_rows.append({
                    "Day": f"{WEEKDAYS[day.weekday]} {day.local_date:%d %b}",
                    "Temp. (°C)": f"{day_hours['temperature_c'].min():.0f} to "
                                  f"{day_hours['temperature_c'].max():.0f}",
                    "Rainy hours": int(day.rainy_hours),
                    "Expected orders": f"{expected:.0f}",
                    "Busiest hour": f"{peak_hour}:00",
                    "Units: hot / ice / alcohol": " / ".join(t.split(" ")[1] for t in group_text),
                    "Suggested offers": "; ".join(offers) or "—",
                })
            plan = pd.DataFrame(plan_rows)

            fig, ax = plt.subplots(figsize=(8, 3.4))
            ax.bar(plan["Day"], plan["Expected orders"].astype(float), color=BLUE, width=0.7,
                   edgecolor=SURFACE, linewidth=2)
            style(ax, "Expected orders, coming week", ylabel="Orders")
            chart_week = save(fig, out, "forecast_coming_week.png")

            match_list = "\n".join(
                f"- {m['name']}, {m['starts_at'].tz_convert(BERLIN):%a %d %b %H:%M}"
                for _, m in matches.iterrows()) or "- none"
            upcoming_section = f"""Real inputs fetched {datetime.now():%Y-%m-%d %H:%M}:
DWD weather {"/".join(observation_types)} for station Bremen ({STATION}) via Bright Sky,
Werder Bremen home matches from OpenLigaDB, and the city events in `local_events`.

Werder home matches this week:
{match_list}

![Coming week](forecast_coming_week.png)

{md(plan)}

The order numbers come from a model trained on the
{"synthetic" if args.source == "SYNTHETIC" else args.source.lower()} history, so they are
{"simulation-based" if args.source == "SYNTHETIC" else "estimates"}. Weather forecasts are less
certain than measurements, especially more than three days ahead."""
        except Exception as error:  # network or API problem: keep the rest of the report
            upcoming_section = f"_Could not plan the coming week: {error}_"

    # ---- 5. Report
    synthetic = args.source == "SYNTHETIC"
    notice = (
        "> **Simulation results.** The models learn from synthetic orders. Their accuracy\n"
        "> shows how well the method works on data with known patterns; it is not a\n"
        "> measured accuracy for the real café. No costs or margins are known, so no\n"
        "> profit effect is claimed for any offer.\n" if synthetic else "")

    report = f"""# Café Stecker — forecasts and offer suggestions

Generated {datetime.now():%Y-%m-%d %H:%M} from database `{args.dbname}`, source `{args.source}`.

{notice}
## 1. How well can we forecast orders?

Models are trained on **{first:%d %b %Y} – {test_start - timedelta(days=1):%d %b %Y}** and tested on
**{test_start:%d %b %Y} – {last:%d %b %Y}**, which they have not seen.

| Model | How it forecasts |
|---|---|
| Same slot, last 4 weeks | Average of the same weekday and hour in the previous four weeks (one day ahead) |
| Hour + weekday | Poisson regression on hour of day and weekday |
| Hour + weekday + weather + events | Adds measured temperature and rain, city events and Werder home matches |

{md(hourly_scores)}

MAE = average absolute error; MAPE = average error in % of actual orders;
Bias = average over- (+) or under- (−) forecast. For the weather model the test
uses the **measured** weather of each hour. A real forecast would use a weather
forecast instead, so real errors will be somewhat larger.

![Test period]({chart_test})

## 2. Product groups (units per day)

Daily units per group, forecast from weekday, average temperature, rainy hours,
events and match hours. Compared with a plain weekday average over the training period.

{md(group_scores)}

"Change per +5 °C" is the model's estimated effect of a 5 °C warmer day on
that group's daily units, other things equal.

## 3. Offer suggestions

Each rule states the evidence it is based on. Evidence is calculated from the
confirmed orders in the database.

{md(rules_table)}

### What is bought together

Lift = how much more often two product groups appear in the same order than
if guests chose them independently (1.00 = no connection).

{md(basket_show)}

To measure whether an offer works, the website would need to record which
offer was active and compare sales against the forecast for that day.
That tracking is not built yet.

## 4. The coming week

{upcoming_section}
"""
    path = Path(out) / "forecast_report.md"
    path.write_text(report, encoding="utf-8")
    print(f"Wrote {path}", file=sys.stderr)
    print(hourly_scores.to_string(index=False), file=sys.stderr)


if __name__ == "__main__":
    main()
