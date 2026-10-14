# Speisekarte — Café Stecker

A digital menu and order analytics project built with React, TypeScript,
Spring Boot and PostgreSQL.

The planned application lets customers prepare an order and show its QR
code to a waiter. The waiter reviews the order, makes any changes, enters
it into the official system, and confirms the final details in this website.

Confirmed order data will support analysis by time, weather, temperature,
football matches and nearby events, followed by offer recommendations.

## Currently implemented

- PostgreSQL-backed menu returned through `GET /api/menu`.
- Import script containing 107 products from the supplied menu spreadsheet.
- Categories, subcategories, descriptions, sizes, notes and availability.
- Frontend menu loading, error handling and retry.
- Quantity selection and a total calculated in integer cents.
- Responsive product cards and a fixed total bar.

Selections currently live in the menu page's state.
Order saving, order QR codes, staff access and analytics are planned.

## Project structure

| Path | Purpose |
|---|---|
| `frontend/` | React, TypeScript and Vite application |
| `backend/` | Java and Spring Boot application |
| `backend/src/main/resources/db/` | Manually executed SQL scripts |
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

The local profile uses `spring.jpa.hibernate.ddl-auto=update` to create
or adjust tables during development.

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

## Build and checks

Backend, starting from the repository root:

```bash
cd backend
./gradlew build
```

Spring integration tests use the `test` profile and an in-memory H2 database.
Use the local PostgreSQL setup to verify PostgreSQL-specific behaviour.

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

## Planned order workflow

1. Customer selects products and adds requests.
2. Website saves a pending order and displays its QR code.
3. Waiter scans the QR and opens a staff-only order page.
4. Waiter adjusts the order and enters it into the official system.
5. Waiter confirms the final quantities and prices in this website.

Planned statuses: `PENDING`, `CONFIRMED` and `CANCELLED`.

Confirmation means the waiter has recorded the order in the official system.
Payment information would require separate tracking.

## Planned analytics

- Product quantities and confirmed order values by hour and weekday.
- Comparisons across temperature, rain, football matches and nearby events.
- Forecasts and offer suggestions with supporting data.
- Reproducible synthetic historical orders for demonstration and evaluation.

Generated orders will be labelled `SYNTHETIC`.
Real and synthetic data will be distinguishable in reports.
Results from simulated data will be described as simulation results.

## Work split and remaining tasks

Frontend work:
- Customer ordering and QR display.
- Waiter review, modification and confirmation pages.
- Dashboard presentation, mobile checks and deployment.
- Update home-page links currently targeting `/menu#torten`,
  `/menu#kuchen` and `/menu#gebaeck` to suitable destinations.

Data and analytics work:
- Synthetic historical order generation.
- Weather, match and nearby-event data.
- Analysis, forecasting and offer recommendation logic.
- Analytics endpoints and chart data.

Shared work:
- Order database model and Spring Boot order API.
- Staff access and server-side price handling.
- Review missing menu details and actual availability.