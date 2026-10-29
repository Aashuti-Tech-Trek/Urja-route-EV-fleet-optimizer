# API Examples

The examples assume a local server at `http://localhost:8080` and a local development operator key. The simple key format is for local evaluation only.

## Health and OpenAPI

```bash
curl -i http://localhost:8080/actuator/health
curl -i http://localhost:8080/v3/api-docs
```

## Register a vehicle

```bash
curl -i -X POST http://localhost:8080/api/v1/vehicles \
  -H 'Content-Type: application/json' \
  -H 'X-API-KEY: key-operator-demo-tenant' \
  -d '{
    "id":"EV-001",
    "currentSoc":0.82,
    "batteryCapacityKwh":60,
    "efficiencyKwhPerKm":0.18,
    "maxChargeRateKw":60,
    "currentLocationId":"BELLANDUR",
    "availableFrom":"2026-10-29T08:00:00Z"
  }'
```

## Register a trip

```bash
curl -i -X POST http://localhost:8080/api/v1/trips \
  -H 'Content-Type: application/json' \
  -H 'X-API-KEY: key-operator-demo-tenant' \
  -d '{
    "id":"TRIP-001",
    "pickupLocationId":"BELLANDUR",
    "dropoffLocationId":"E_CITY",
    "pickupWindowStart":"2026-10-29T09:00:00Z",
    "pickupWindowEnd":"2026-10-29T09:20:00Z",
    "deadline":"2026-10-29T10:00:00Z",
    "distanceKm":14.5,
    "passengerCount":1
  }'
```

## Register a charger hub and read overview

```bash
curl -i -X POST http://localhost:8080/api/v1/hubs \
  -H 'Content-Type: application/json' \
  -H 'X-API-KEY: key-operator-demo-tenant' \
  -d '{"id":"HUB-001","name":"Bellandur Hub","locationId":"BELLANDUR","totalChargers":8}'

curl -i http://localhost:8080/api/v1/hubs -H 'X-API-KEY: key-viewer-demo-tenant'
curl -i http://localhost:8080/api/v1/overview -H 'X-API-KEY: key-viewer-demo-tenant'
```

## Propose and accept a plan

```bash
curl -i -X POST http://localhost:8080/api/v1/plans/propose \
  -H 'Content-Type: application/json' \
  -H 'X-API-KEY: key-operator-demo-tenant' \
  -d '{"planningHorizonStart":"2026-10-29T08:00:00Z","planningHorizonEnd":"2026-10-29T16:00:00Z","vehicleIds":[],"tripIds":[]}'
```

Copy `proposalId` from the response. Only accept a proposal with `valid: true`:

```bash
curl -i -X POST 'http://localhost:8080/api/v1/plans/REPLACE_WITH_PROPOSAL_ID/accept' \
  -H 'Content-Type: application/json' \
  -H 'X-API-KEY: key-operator-demo-tenant' \
  -H 'Idempotency-Key: accept-demo-001' \
  -d '{"expectedCurrentVersion":0}'
```

## Live recovery events

```bash
curl -i -X POST http://localhost:8080/api/v1/replanning/battery-drift \
  -H 'Content-Type: application/json' -H 'X-API-KEY: key-operator-demo-tenant' \
  -d '{"vehicleId":"EV-001","observedSoc":0.54,"eventTime":"2026-10-29T09:10:00Z"}'

curl -i -X POST http://localhost:8080/api/v1/replanning/charger-failure \
  -H 'Content-Type: application/json' -H 'X-API-KEY: key-operator-demo-tenant' \
  -d '{"hubId":"HUB-001","outageStart":"2026-10-29T09:15:00Z","estimatedRestoration":"2026-10-29T10:00:00Z"}'
```

Use future timestamps appropriate for the actual test scenario. API timestamps are ISO-8601 instants; display times in the dashboard are localized to Asia/Kolkata.
