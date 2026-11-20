#!/usr/bin/env python3
"""Download real Bremen context data for the order analysis.

Writes two CSV files to backend/src/main/resources/context/:

  weather_bremen_hourly.csv
      Hourly observations of the Deutscher Wetterdienst (DWD) station
      "Bremen" (station ID 00691, WMO 10224), fetched through the free
      Bright Sky API (https://brightsky.dev). Data: © Deutscher Wetterdienst,
      Open Data, https://www.dwd.de/opendata.

  football_bremen.csv
      SV Werder Bremen home matches in the Weserstadion from OpenLigaDB
      (https://www.openligadb.de), Bundesliga and DFB-Pokal.

The third file, events_bremen.csv, is maintained by hand with a source
link for every row.

Usage (from the repository root, standard library only):

    python3 scripts/fetch_context_data.py 2025-11-01 2026-10-31
"""

import csv
import json
import sys
import urllib.request
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

OUT = Path(__file__).resolve().parent.parent / "backend/src/main/resources/context"
STATION = "00691"
BERLIN = ZoneInfo("Europe/Berlin")
USER_AGENT = "speisekarte-context-fetch/1.0"


def get_json(url):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def month_starts(first, last):
    current = first.replace(day=1)
    while current <= last:
        yield current
        current = (current.replace(day=28) + timedelta(days=4)).replace(day=1)


def fetch_weather(first, last):
    rows = {}
    for start in month_starts(first, last):
        end = (start.replace(day=28) + timedelta(days=4)).replace(day=1)
        # Start one day early so the first local (Europe/Berlin) hours,
        # which are still the previous day in UTC, are included.
        query_start = start - timedelta(days=1) if start <= first else start
        url = (
            "https://api.brightsky.dev/weather"
            f"?dwd_station_id={STATION}"
            f"&date={query_start.isoformat()}T00:00:00%2B00:00"
            f"&last_date={end.isoformat()}T00:00:00%2B00:00"
            "&tz=Etc/UTC"
        )
        data = get_json(url)
        stations = {s["id"]: s for s in data["sources"]}
        for hour in data["weather"]:
            moment = datetime.fromisoformat(hour["timestamp"]).astimezone(timezone.utc)
            local_day = moment.astimezone(BERLIN).date()
            if not (first <= local_day <= last):
                continue
            source = stations.get(hour["source_id"], {})
            rows[moment] = {
                "observed_at_utc": moment.strftime("%Y-%m-%dT%H:%M:%SZ"),
                "temperature_c": hour["temperature"],
                "precipitation_mm": hour["precipitation"],
                "sunshine_min": hour["sunshine"],
                "cloud_cover_pct": hour["cloud_cover"],
                "wind_speed_kmh": hour["wind_speed"],
                "condition": hour["condition"],
                "station": source.get("dwd_station_id") or source.get("station_name") or "",
            }
        print(f"weather {start:%Y-%m}: {len(data['weather'])} hours", file=sys.stderr)
    return [rows[k] for k in sorted(rows)]


def fetch_football(first, last):
    competitions = []
    for season in range(first.year - 1, last.year + 1):
        competitions += [("bl1", season, "Bundesliga"), ("dfb", season, "DFB-Pokal")]

    matches = {}
    for league, season, name in competitions:
        try:
            data = get_json(f"https://api.openligadb.de/getmatchdata/{league}/{season}")
        except Exception as error:  # a season that does not exist yet
            print(f"skip {league}/{season}: {error}", file=sys.stderr)
            continue
        for match in data:
            home = match["team1"]["teamName"]
            if "Werder" not in home:
                continue
            kickoff = datetime.fromisoformat(match["matchDateTimeUTC"].replace("Z", "+00:00"))
            if not (first <= kickoff.date() <= last):
                continue
            goals = [r for r in match.get("matchResults") or [] if r.get("resultTypeID") == 2]
            score = f"{goals[0]['pointsTeam1']}:{goals[0]['pointsTeam2']}" if goals else ""
            matches[kickoff] = {
                "kickoff_utc": kickoff.strftime("%Y-%m-%dT%H:%M:%SZ"),
                "competition": name,
                "season": f"{season}/{season + 1}",
                "home_team": home,
                "away_team": match["team2"]["teamName"],
                "venue": "Weserstadion",
                "final_score": score,
                "openligadb_match_id": match["matchID"],
            }
    return [matches[k] for k in sorted(matches)]


def write_csv(path, rows):
    with open(path, "w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)
    print(f"wrote {len(rows)} rows to {path}", file=sys.stderr)


def main():
    first = date.fromisoformat(sys.argv[1])
    last = date.fromisoformat(sys.argv[2])
    OUT.mkdir(parents=True, exist_ok=True)
    write_csv(OUT / "weather_bremen_hourly.csv", fetch_weather(first, last))
    write_csv(OUT / "football_bremen.csv", fetch_football(first, last))


if __name__ == "__main__":
    main()
