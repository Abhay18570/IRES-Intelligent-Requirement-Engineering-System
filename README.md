# IRES

IRES is a React/Vite frontend with a Spring Boot 3 backend. The backend uses
PostgreSQL and applies its schema with Flyway at startup.

## Local backend setup (recommended: Docker Compose)

Requirements: Java 17+, Maven, and Docker Desktop (or another running Docker
engine with Compose).

1. Create the local environment file if it does not exist:

   ```sh
   cp .env.example .env
   ```

2. Replace `JWT_SECRET` in `.env` with a strong secret. For local development,
   the database defaults are:

   ```dotenv
   DB_HOST=localhost
   DB_PORT=5432
   DB_NAME=ires
   DB_USER=ires
   DB_PASSWORD=change-me
   ```

3. Start PostgreSQL and wait for its health check:

   ```sh
   docker compose up -d --wait postgres
   docker compose ps
   ```

4. Start Spring Boot from the repository root:

   ```sh
   cd backend
   mvn clean spring-boot:run
   ```

The `dev` profile is active by default. It explicitly imports `.env` from the
repository root, so no manual `export` step is required. Real shell or IDE
environment variables take precedence over `.env`. The resulting JDBC URL is
`jdbc:postgresql://localhost:5432/ires`.

To test the database directly through the container:

```sh
docker compose exec postgres psql -U ires -d ires -c 'select current_database(), current_user;'
```

Stop the local database without deleting its data with `docker compose down`.
The named volume `ires_postgres_data` preserves the database.

## Local PostgreSQL alternative on macOS

Docker Compose is the project default. To use Homebrew PostgreSQL instead:

```sh
brew list --versions postgresql@16
brew install postgresql@16                 # only if the previous command finds nothing
brew services start postgresql@16
$(brew --prefix postgresql@16)/bin/pg_isready -h localhost -p 5432
$(brew --prefix postgresql@16)/bin/psql postgres -c "CREATE ROLE ires LOGIN PASSWORD 'change-me';"
$(brew --prefix postgresql@16)/bin/createdb -O ires ires
PGPASSWORD=change-me $(brew --prefix postgresql@16)/bin/psql -h localhost -p 5432 -U ires -d ires -c 'select current_database(), current_user;'
```

If the role or database already exists, PostgreSQL will report that; skip the
corresponding creation command. Verify the listening process with:

```sh
lsof -nP -iTCP:5432 -sTCP:LISTEN
```

Keep `.env` aligned with any non-default local credentials. Do not run the
Homebrew and Compose PostgreSQL servers on port 5432 at the same time.

## Frontend

See [frontend/README.md](frontend/README.md) for frontend commands.
