"""Code shared by the analysis scripts: database access through psql, the
café's opening calendar, event flags per hour, and chart styling."""

import io
import os
import shutil
import subprocess
from datetime import date, datetime, timedelta
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402
import pandas as pd  # noqa: E402

HERE = Path(__file__).resolve().parent
SQL_DIR = HERE / "sql"
BERLIN = "Europe/Berlin"
WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun",
          "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]

# Opening calendar, same assumption as the generator (CafeCalendar.java).
WEEKDAY_OPEN, SUNDAY_OPEN, LAST_ORDER = 9, 13, (17, 30)

# Product groups used for demand and offers (by menu subcategory).
GROUPS = {
    "Hot drinks": {"Azul Kaffee", "Schokolade", "Zum Aufwärmen", "Tee (Glas)",
                   "Tee auf Stövchen traditionell"},
    "Cold drinks": {"Alkoholfreie Getränke", "Fruchtsäfte"},
    "Alcohol": {"Biere", "Weine", "Sekt & Prosecco", "Longdrinks"},
    "Breakfast": {"Frühstück", "Belegte Brötchen"},
    "Savoury": {"Kleine, herzhafte Leckereien", "Klassisches"},
    "Bar snacks": {"Unsere Empfehlungen zu Bier & Wein"},
}
ICE_CATEGORY = "Eis & Desserts"

# Chart style (reference data-viz palette, light mode).
SURFACE = "#fcfcfb"
TEXT = "#0b0b0b"
TEXT_SECONDARY = "#52514e"
GRID = "#e4e3df"
BLUE = "#2a78d6"
ORANGE = "#eb6834"
AQUA = "#1baf7a"
BLUE_RAMP = ["#cde2fb", "#9ec5f4", "#6da7ec", "#3987e5", "#256abf", "#184f95", "#0d366b"]


def product_group(category, subcategory):
    if category == ICE_CATEGORY:
        return "Ice cream & desserts"
    for name, subcategories in GROUPS.items():
        if subcategory in subcategories:
            return name
    return "Other"


# ---------------------------------------------------------------- database

class Database:
    """Runs SQL through the psql command-line client."""

    def __init__(self, args):
        self.psql = args.psql or shutil.which("psql") \
            or "/opt/homebrew/opt/postgresql@17/bin/psql"
        self.base = [self.psql, "-X", "-q", "-v", "ON_ERROR_STOP=1",
                     "-h", args.host, "-p", str(args.port),
                     "-U", args.user, "-d", args.dbname]
        self.env = dict(os.environ)
        password = os.environ.get("STECKER_DB_PASSWORD")
        if password:
            self.env["PGPASSWORD"] = password

    def run_file(self, path):
        subprocess.run(self.base + ["-f", str(path)], env=self.env, check=True,
                       stdout=subprocess.DEVNULL)

    def run_script(self, script):
        """Runs SQL (including COPY ... FROM STDIN data) given as text."""
        subprocess.run(self.base + ["-f", "-"], env=self.env, check=True, input=script,
                       text=True, stdout=subprocess.DEVNULL)

    def query(self, sql):
        result = subprocess.run(self.base + ["--csv", "-c", sql], env=self.env,
                                check=True, capture_output=True, text=True)
        frame = pd.read_csv(io.StringIO(result.stdout))
        for column in frame.columns:
            if frame[column].dtype == object and set(frame[column].dropna()) <= {"t", "f"}:
                frame[column] = frame[column] == "t"
        return frame


def add_database_arguments(parser):
    parser.add_argument("--dbname", default="stecker")
    parser.add_argument("--user", default="stecker_app")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", default=5432, type=int)
    parser.add_argument("--psql", help="path to psql if it is not on PATH")


# ---------------------------------------------------------------- calendar

def easter_sunday(year):
    a, b, c = year % 19, year // 100, year % 100
    d, e = b // 4, b % 4
    f = (b + 8) // 25
    g = (b - f + 1) // 3
    h = (19 * a + b - d - g + 15) % 30
    i, k = c // 4, c % 4
    l = (32 + 2 * e + 2 * i - h - k) % 7
    m = (a + 11 * h + 22 * l) // 451
    month = (h + l - 7 * m + 114) // 31
    day = (h + l - 7 * m + 114) % 31 + 1
    return date(year, month, day)


def is_open(day):
    fixed = {(1, 1), (5, 1), (10, 3), (10, 31), (12, 25), (12, 26)}
    if (day.month, day.day) in fixed:
        return False
    easter = easter_sunday(day.year)
    return day not in {easter - timedelta(days=2), easter + timedelta(days=1),
                       easter + timedelta(days=39), easter + timedelta(days=50)}


def open_hours(first, last):
    """Every opening hour with its local start, the UTC hour and minutes open."""
    rows = []
    day = first
    while day <= last:
        if is_open(day):
            start = SUNDAY_OPEN if day.weekday() == 6 else WEEKDAY_OPEN
            for hour in range(start, LAST_ORDER[0] + 1):
                minutes = 60 if hour < LAST_ORDER[0] else LAST_ORDER[1]
                rows.append({"local_start": datetime(day.year, day.month, day.day, hour),
                             "minutes_open": minutes})
        day += timedelta(days=1)
    hours = pd.DataFrame(rows)
    local = pd.to_datetime(hours["local_start"]).dt.tz_localize(BERLIN)
    hours["hour_start_utc"] = local.dt.tz_convert("UTC")
    # Same point the generator uses for event flags: the middle of the open part.
    hours["middle_utc"] = hours["hour_start_utc"] + pd.to_timedelta(
        hours["minutes_open"] // 2, unit="m")
    hours["local_date"] = local.dt.date
    hours["hour"] = local.dt.hour
    hours["weekday"] = local.dt.weekday
    hours["month"] = local.dt.month
    return hours


def add_event_flags(hours, events):
    """Adds 0/1 columns for each event type active in the middle of each hour.

    events: DataFrame with type, starts_at, ends_at (UTC timestamps).
    """
    middle = hours["middle_utc"]

    def active(kind, start_shift=pd.Timedelta(0), end_column="ends_at",
               end_shift=pd.Timedelta(0)):
        flag = np.zeros(len(hours), dtype=int)
        for _, event in events[events["type"] == kind].iterrows():
            start = event["starts_at"] + start_shift
            end = event[end_column] + end_shift
            flag |= ((middle >= start) & (middle < end)).to_numpy().astype(int)
        return flag

    hours = hours.copy()
    hours["christmas_market"] = active("CHRISTMAS_MARKET")
    hours["fair"] = active("FAIR")
    hours["festival"] = active("FESTIVAL")
    hours["parade"] = active("PARADE")
    hours["pre_match"] = active("FOOTBALL", -pd.Timedelta(hours=4),
                                "starts_at", -pd.Timedelta(minutes=30))
    hours["during_match"] = active("FOOTBALL") * (1 - hours["pre_match"])
    return hours


def load_events(db):
    events = db.query("SELECT type, name, starts_at, ends_at FROM local_events")
    events["starts_at"] = pd.to_datetime(events["starts_at"], utc=True)
    events["ends_at"] = pd.to_datetime(events["ends_at"], utc=True)
    return events


def load_weather(db):
    weather = db.query(
        "SELECT observed_at, temperature_c, precipitation_mm FROM weather_hourly")
    weather["observed_at"] = pd.to_datetime(weather["observed_at"], utc=True)
    return weather


# ---------------------------------------------------------------- charts

def style(ax, title, xlabel=None, ylabel=None):
    ax.set_facecolor(SURFACE)
    ax.set_title(title, loc="left", color=TEXT, fontsize=12, fontweight="bold", pad=12)
    if xlabel:
        ax.set_xlabel(xlabel, color=TEXT_SECONDARY)
    if ylabel:
        ax.set_ylabel(ylabel, color=TEXT_SECONDARY)
    for side in ("top", "right"):
        ax.spines[side].set_visible(False)
    for side in ("left", "bottom"):
        ax.spines[side].set_color(GRID)
    ax.tick_params(colors=TEXT_SECONDARY, length=0)
    ax.grid(axis="y", color=GRID, linewidth=0.8)
    ax.set_axisbelow(True)


def save(fig, out, name):
    fig.patch.set_facecolor(SURFACE)
    fig.tight_layout()
    fig.savefig(Path(out) / name, dpi=150)
    plt.close(fig)
    return name


def euro(value):
    return f"€{value:,.2f}"


def md(frame, **kwargs):
    return frame.to_markdown(index=False, **kwargs)
