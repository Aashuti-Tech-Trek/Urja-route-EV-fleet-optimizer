# UrjaRoute Operations Runbook

## Prerequisites

- Docker Desktop / Docker Engine with Compose v2.
- For local non-container runs: Java 21. Maven wrapper downloads Maven and dependencies on first use.
- Copy `.env.example` to `.env` and set a unique local database password. The defaults are development-only.

## Start the complete stack

```bash
cp .env.example .env
# Edit .env before using the service beyond a private local machine.
docker compose up --build -d
docker compose ps
docker compose logs --tail 200 app
```

Open the dashboard at `http://localhost:8080`, Swagger at `http://localhost:8080/swagger-ui/index.html`, and health at `http://localhost:8080/actuator/health`.

The dashboard starts with demo credentials (`key-admin-demo-tenant`) for local exploration. The current key format is intentionally simple and must not be considered secure secret storage. Do not expose this demo service to the public internet. For a controlled environment, set `URJAROUTE_DEMO_KEY_MODE=false` and configure explicit `URJAROUTE_ADMIN_API_KEY` / `URJAROUTE_ADMIN_TENANT_ID`, `URJAROUTE_OPERATOR_API_KEY` / `URJAROUTE_OPERATOR_TENANT_ID`, and optional viewer equivalents. Store those values in a secret manager. This fixed-key allow-list is still not a substitute for a full identity provider. Enforce TLS, rotate secrets, and use managed database credentials.

## Shutdown and persistence

```bash
docker compose down
```

The PostgreSQL data volume persists across normal shutdowns. To intentionally delete local database data:

```bash
docker compose down -v
```

## Manual verification checklist

1. Open `/` and verify the responsive dashboard renders.
2. Open `/actuator/health`; expected status is `UP` when the database and migrations are healthy.
3. Open Swagger UI and authorize with a local development key.
4. Register a vehicle, trip, and charger hub.
5. Generate a proposal; check the independent validator result.
6. Accept a valid proposal with `expectedCurrentVersion: 0` and an `Idempotency-Key` header.
7. Repeat the same acceptance with the same key and payload; expect the cached response.
8. Change the payload while reusing the same idempotency key; expect `409 Conflict`.
9. Use a viewer key for a read and attempt a write; the write should return `403 Forbidden`.
10. Use two tenant IDs and verify the data does not cross tenant boundaries.
11. Test the battery-drift and charger-failure endpoints against a populated demo tenant.
12. Export trip demand to CSV from the dashboard.

## Troubleshooting

- **API says 401:** ensure the `X-API-KEY` is set. Local examples use `key-admin-demo-tenant` or `key-operator-demo-tenant`.
- **API says 403:** the role is authenticated but does not have permission for the requested mutation.
- **Dashboard shows demo data:** the backend request failed or credentials are not valid. Use the connection settings button and check browser developer tools and app logs.
- **Database startup is slow:** check `docker compose logs postgres` and wait for its health check before restarting the app.
- **Maven wrapper cannot download:** restore DNS/network access to Maven Central and rerun `./mvnw clean verify` (Windows: `./mvnw.cmd clean verify`).
- **Port already in use:** set `SERVER_PORT` or `POSTGRES_PORT` in `.env`.

## Production hardening still required

- Replace predictable demo API-key parsing with stored, hashed, revocable keys or OAuth2/OIDC JWT validation.
- Configure TLS at the ingress/reverse proxy and restrictive CORS if the UI/API are split across origins.
- Use secrets management; never commit `.env` or production credentials.
- Add database backups, restore drills, migration rollback procedures and alert routing.
- Run load tests on documented hardware with representative data before claiming the 30-second / 2-second targets.
