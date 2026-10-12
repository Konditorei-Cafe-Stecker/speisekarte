# Speisekarte

## Project Description
This is the Speisekarte project for the stecker-konditorei. It serves as a digital menu for the bakery, showcasing various products and offerings.

## Project Structure
- **index.html**: Main HTML file displaying the menu.
- **styles/**: Directory containing stylesheets.
- **scripts/**: Directory for JavaScript files.
- **images/**: Directory for images of products.
- **README.md**: Information about the project.

## Java backend

The backend uses Java 21, Spring Boot, Spring Data JPA, and PostgreSQL.

Menu products are stored in PostgreSQL and returned through `GET /api/menu`,
ordered by ID.

### PostgreSQL setup on macOS

Requirements: Java 21 and Homebrew.

Install and start PostgreSQL:

```bash
brew install postgresql@17
brew services start postgresql@17
```

Open PostgreSQL's command-line client:

```bash
/opt/homebrew/opt/postgresql@17/bin/psql -d postgres
```

Run the following lines one at a time inside psql.
At `\password`, choose your own password and enter it twice when prompted:

```text
CREATE ROLE stecker_app WITH LOGIN;
\password stecker_app
CREATE DATABASE stecker OWNER stecker_app;
\q
```

The local Spring profile connects to database `stecker` on
`localhost:5432` using account `stecker_app`.

Its password configuration is:

```properties
spring.datasource.password=${STECKER_DB_PASSWORD}
```

Keep the real password out of project files and Git commits.

### Start the backend

From the repository root:

```bash
cd backend
```

In macOS's zsh Terminal, run this command first, then enter your database
password when prompted. Your typing will be hidden:

```bash
read -r -s 'STECKER_DB_PASSWORD?Database password: '
```

Then export the variable and start Spring Boot with the local profile:

```bash
export STECKER_DB_PASSWORD
./gradlew bootRun --args='--spring.profiles.active=local'
```

Keep this Terminal tab running. Stop the backend with Control+C.

The local profile uses `spring.jpa.hibernate.ddl-auto=update` to create
or adjust tables during development.

### Add sample menu products

After the backend starts successfully:

1. Connect pgAdmin to database `stecker` using account `stecker_app`.
2. Open the database's Query Tool.
3. Execute the contents of
   `backend/src/main/resources/db/sample-menu.sql`.

The script adds Cappuccino, Käsekuchen, and Buttercroissant only when
the `menu_items` table is empty. It then displays the saved rows.

This script is run manually.

### Check the API

Open these addresses in your browser:

- Health: http://localhost:8080/api/health
- Menu: http://localhost:8080/api/menu

The menu endpoint returns an empty JSON array when there are no products.
After adding sample products, it returns those database records.

### Build and test

From the `backend` directory:

```bash
./gradlew build
```

This compiles the backend, runs its automated tests, and builds the
application package.

Spring integration tests activate the `test` profile and use an H2
database in memory. Tests cover application startup, repository
persistence and ID ordering, and service delegation.

Use the local PostgreSQL setup to check PostgreSQL-specific behavior.