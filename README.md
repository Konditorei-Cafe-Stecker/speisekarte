# Speisekarte — Café Stecker

A digital menu and order analytics project built with React, TypeScript,
Spring Boot and PostgreSQL.

The planned application lets customers prepare an order and show its QR
code to a waiter. The waiter reviews the order, makes any changes, enters
it into the official system, and confirms the final details in this website.

Confirmed order data supports analysis by time, weather, temperature,
football matches and nearby events, forecasts and offer suggestions, and a
planning page for the owner.

## Currently implemented

- PostgreSQL-backed menu returned through `GET /api/menu`.
- Import script containing 107 products from the supplied menu spreadsheet.
- Categories, subcategories, descriptions, sizes, notes and availability.
- Speisekarte with search, categories, quantities kept for the browser
  session, and a total calculated in integer cents.
- Orders saved through `POST /api/orders` with prices from the database.
- Staff area at `/personal` with personal accounts, sign-in, own
  passwords, and team management for admins.
- The order workflow below: QR code and short code for the guest, the
  waiter's order list and order page, confirmation and cancellation, and
  a live status on the guest's phone.

- Orders record whether they are real (`REAL`) or simulated (`SYNTHETIC`).
- Real Bremen context: hourly DWD weather, Werder Bremen home matches and
  city events (`weather_hourly`, `local_events`).
- A reproducible synthetic order history for Nov 2025 – Oct 2026, generated
  from the real menu and context. See [docs/synthetic-data.md](docs/synthetic-data.md).
- Analysis, forecasts and offer suggestions: SQL views, data-quality
  checks and Python reports. See [analysis/README.md](analysis/README.md).
- Planning page for admins at `/personal/planung`: expected revenue,
  service staff per hour and product needs for the next 7 days.

## Project structure

| Path | Purpose |
|---|---|
| `frontend/` | React, TypeScript and Vite application |
| `backend/` | Java and Spring Boot application |
| `backend/src/main/resources/db/` | Manually executed SQL scripts |
| `backend/src/main/resources/db/migration/` | Flyway migrations |
| `backend/src/main/resources/context/` | Real Bremen weather, football and event data (CSV) |
| `analysis/` | SQL views, quality checks, analysis and forecast scripts |
| `scripts/` | Download script for the context data |
| `docs/` | Synthetic data: sources, assumptions, how to run |
| `README.md` | Project description and local setup |

## Requirements

The local setup has been tested with:

- Java 21
- Node.js 24 and npm
- PostgreSQL 17
- Homebrew for the macOS database installation

Commands below assume you start from the repository root.

## PostgreSQL setup on macOS

For a new local database, install and start PostgreSQL:

```bash
brew install postgresql@17
brew services start postgresql@17
```

Open PostgreSQL's command-line client:

```bash
/opt/homebrew/opt/postgresql@17/bin/psql -d postgres
```

Run these lines one at a time inside psql.
At `\password`, choose a password and enter it twice when prompted:

```text
CREATE ROLE stecker_app WITH LOGIN;
\password stecker_app
CREATE DATABASE stecker OWNER stecker_app;
\q
```

Skip database creation if this role and database already exist.

The local Spring profile connects to database `stecker` on
`localhost:5432` using account `stecker_app`.

The password is supplied through an environment variable:

```properties
spring.datasource.password=${STECKER_DB_PASSWORD}
```

Keep the actual password out of project files and Git commits.

## Start the backend

From the repository root:

```bash
cd backend
```

In macOS's zsh Terminal, run this command and enter the database password.
Typing will be hidden:

```bash
read -r -s 'STECKER_DB_PASSWORD?Database password: '
```

Then export the variable and start the backend:

```bash
export STECKER_DB_PASSWORD
./gradlew bootRun --args='--spring.profiles.active=local'
```

Keep this Terminal tab running. Stop the backend with Control+C.

## Database migrations

Flyway creates and changes the tables when the backend starts. The
migrations live in `backend/src/main/resources/db/migration` and run in
version order (`V1__baseline.sql`, `V2__…`). Hibernate only checks that the
entities match the tables (`ddl-auto=validate`) and never changes them.

To change the database, add a new migration file with the next version
number. Never edit a migration that has already run anywhere, because
Flyway refuses to start when a file differs from the recorded version.

Databases created before migrations were introduced already contain the
version 1 tables. On the first start Flyway records them as version 1 in a
new `flyway_schema_history` table and leaves the data unchanged.

## Import the menu

Start the backend successfully before importing products.

1. Connect pgAdmin to database `stecker` using account `stecker_app`.
2. Open the database's Query Tool.
3. Execute:
   `backend/src/main/resources/db/stecker_menu_import.sql`.

This script runs manually. It imports 107 products across:

- Getränke
- Speisen
- Eis & Desserts

Existing matching products are skipped. Their stored prices are not updated
by rerunning the import.

Imported products initially have `available = true`.
Some incomplete source entries were excluded, and unclear source notes
should be reviewed before publication.

Surcharges mentioned in notes are informational.
The current quantity selector totals the stored base prices.

The older `db/sample-menu.sql` script provides three development samples.
Use the full import for the actual menu. The full import preserves any
products already in the database.

## Start the frontend

Open another Terminal tab at the repository root:

```bash
cd frontend
npm ci
npm run dev
```

Open:

- Website: http://localhost:5173
- Menu: http://localhost:5173/menu
- Staff area: http://localhost:5173/personal

Keep the backend running too.

Vite forwards development requests beginning with `/api` to
`http://localhost:8080`.

Production hosting will need its own API routing configuration.

## Check the API

| Address | Purpose |
|---|---|
| `http://localhost:8080/api/health` | Backend health |
| `http://localhost:8080/api/menu` | Products from PostgreSQL |
| `http://localhost:5173/api/menu` | Menu through the Vite development proxy |

Menu records include:

`id`, `name`, `description`, `price`, `category`, `subcategory`,
`sizeVolume`, `notes` and `available`.

`POST /api/orders` saves an order. The request lists products and
quantities only; prices always come from the database:

```json
{ "items": [{ "menuItemId": 1, "quantity": 2 }] }
```

It answers `201 Created` with the saved order. Errors are answered as
problem details (`application/problem+json`) with a German `detail` text:
`400` for an invalid request (empty order, quantity outside 1–99, more than
50 different products), `404` for an unknown product and `409` for a
product that is not available.

## Staff accounts

Every staff member has a personal account. Accounts are never deleted,
only deactivated, so records keep pointing at a real person.

| Role | May |
|---|---|
| `STAFF` | sign in to the staff area and work with orders |
| `ADMIN` | additionally create accounts, change roles, deactivate accounts and reset passwords |

New accounts and reset passwords are temporary: at the next sign-in the
person must choose their own (at least 10 characters). There is always at
least one active admin, and nobody can deactivate or demote themselves.
A deactivated account or a reset password ends that person's sessions at
once.

### The first admin

While no account exists, the backend creates one admin from two
environment variables. Set them in the same Terminal tab before starting
the backend (typing stays hidden):

```bash
export STECKER_BOOTSTRAP_ADMIN_USERNAME=chefin
read -r -s 'STECKER_BOOTSTRAP_ADMIN_PASSWORD?Temporary admin password: '
export STECKER_BOOTSTRAP_ADMIN_PASSWORD
```

Optionally set `STECKER_BOOTSTRAP_ADMIN_DISPLAY_NAME` as well. Start the
backend, sign in at http://localhost:5173/personal and choose a new
password. From then on the variables are ignored and can be removed:

```bash
unset STECKER_BOOTSTRAP_ADMIN_USERNAME STECKER_BOOTSTRAP_ADMIN_PASSWORD
```

Further accounts are created by an admin under *Team*.

### Sign-in API

Staff stay signed in with a session cookie for up to 12 hours. Every
changing request must also send the CSRF token: `GET /api/auth/csrf` sets
the `XSRF-TOKEN` cookie, and its value goes back in the `X-XSRF-TOKEN`
header. Sending an order from the Speisekarte is public and exempt.

| Request | Purpose |
|---|---|
| `GET /api/auth/csrf` | Set the CSRF cookie |
| `POST /api/auth/login` | Sign in (form fields `username`, `password`) |
| `POST /api/auth/logout` | Sign out |
| `GET /api/auth/me` | The signed-in person, or `401` |
| `POST /api/auth/password` | Choose an own password |
| `GET, POST /api/admin/staff` | List and create accounts (admin) |
| `PATCH /api/admin/staff/{id}` | Change name, role or active state (admin) |
| `POST /api/admin/staff/{id}/password` | Set a temporary password (admin) |

After 5 failed sign-ins for one username, or 20 from one address, within
15 minutes, sign-in is refused with `429` until the oldest failure is 15
minutes old. Endpoints not listed in `SecurityConfig` are closed.

## Build and checks

Backend, starting from the repository root:

```bash
cd backend
./gradlew build
```

Spring integration tests use the `test` profile and an in-memory H2 database.
The Flyway migrations create its tables, so the tests also check that
migrations and entities match. Use the local PostgreSQL setup to verify
PostgreSQL-specific behaviour.

Frontend, starting from the repository root:

```bash
cd frontend
npm run build
npm run lint
```

Manual frontend checks:

- Products and size variants display correctly.
- Quantities increase and decrease without becoming negative.
- Unavailable products cannot be added.
- Clearing the selection resets quantities and the total.
- One Tasse Filterkaffee and two Cappuccino mit Milch total 11,70 €.
- Backend failures display an error and support retry.
- The layout works at narrow widths and the final product remains accessible.

## Order workflow

1. The guest selects products on the Speisekarte, optionally writes wishes
   for the waiter, and sends the order.
2. The website saves it as `PENDING` and shows a QR code and a
   four-character code (for example `MJ3V`).
3. The waiter scans the QR code with their phone, which opens
   `/personal/bestellung/<order>` (after signing in), or types the code
   into the overview at `/personal`.
4. The waiter checks the order with the guest, changes quantities, removes
   or adds products, and enters it into the café's till system.
5. The waiter confirms it here (`CONFIRMED`), with the final quantities,
   or cancels it (`CANCELLED`). After that the order cannot change.
6. The guest's confirmation updates by itself (it checks every 10 seconds
   while the page is open).

Confirmation means the waiter has recorded the order in the official
system. Payment is not tracked here.

What is stored for each order: the guest's original quantities next to
the confirmed ones (removed lines keep quantity 0, added lines have
original quantity 0), the wishes, and who confirmed or cancelled it when.
Order times are local times in Bremen. Unit prices are copied into the
order when a line is created, so later menu changes do not alter it.

The overview lists today's open orders first, oldest at the top, and marks
orders waiting longer than 10 minutes. Open orders from earlier days (the
guest never showed the code) are listed separately so they can be
cancelled. If two waiters handle the same order, the second one gets a
`409` with an explanation instead of overwriting the first.

| Request | Purpose |
|---|---|
| `GET /api/orders/{order}` | The guest's view of an order (public; the long random order number is the permission) |
| `GET /api/staff/orders?view=open` | Open orders, oldest first |
| `GET /api/staff/orders?view=today` | Confirmed and cancelled today, newest first |
| `GET /api/staff/orders/{order}` | One order with original quantities |
| `GET /api/staff/orders/code/{code}` | An open order by its four-character code |
| `POST /api/staff/orders/{order}/confirm` | Confirm, optionally with `{"items": [...]}` as the final order |
| `POST /api/staff/orders/{order}/cancel` | Cancel |

## Analytics and planning

Generated orders are labelled `SYNTHETIC`, real ones `REAL`; reports keep
them apart, and results from simulated data are called simulation results.

- [docs/synthetic-data.md](docs/synthetic-data.md): what is real (menu,
  weather, matches, events) and what is simulated, and how to generate
  the history.
- [analysis/README.md](analysis/README.md): data-quality checks, the
  analysis report, forecasts and offer suggestions.

### Planning page for admins

`/personal/planung` (admins only, link "Planung" in the staff navigation)
shows for today and the next 6 days:

- **Umsatz:** expected revenue per day with a likely range, plus weather,
  events, Werder home matches and suggested offers.
- **Personalplanung:** service staff per hour, based on the busy case
  (9 of 10 days are quieter) and an adjustable assumption of how many
  orders one person handles per hour (default 6, minimum staff 2).
- **Warenbedarf:** expected units per product for the week and a
  busy-case amount. Ingredients need recipe data, which does not exist yet.

The page reads a forecast that has to be published first. With the backend
having started once (so Flyway created the tables) and `STECKER_DB_PASSWORD`
set, from the repository root:

```bash
python3 analysis/forecast_and_offers.py --publish
```

This fetches the real DWD weather forecast and upcoming Werder matches and
writes the next 7 days into `forecast_days`, `forecast_hours` and
`forecast_products` (`GET /api/admin/forecast` reads them). Run it once a
day. Older forecast days are kept, so forecasts can later be compared with
what happened.

## Work split and remaining tasks

Frontend work:
- Dashboard presentation, mobile checks and deployment.

Data and analytics work:
- Run the analysis on real orders once they exist, and compare published
  forecasts with what happened.
- Track which offer was active, so its effect can be measured.
- Recipes per product, to turn product needs into ingredient amounts.

Shared work:
- Review missing menu details and actual availability.
