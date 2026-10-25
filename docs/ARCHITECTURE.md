# DockMind AI: Architecture

This describes the **target** architecture. **Built so far (M4b):** `api-gateway` (:8080), `shipment-service` (REST,
outbox → Kafka), `dock-scheduling-service` (docks, events → read model, schedules), and the shared
`common`, `common-security` and `scheduling-engine` modules. Event contracts: [EVENTS.md](EVENTS.md).

## 1. Services

```
                 ┌─────────────────────┐
  Angular UI ───▶│     API Gateway     │  JWT validation, rate limiting, correlation IDs
                 └──────────┬──────────┘
          REST (sync)       │
     ┌──────────────┬───────┴───────┬───────────────────┐
     ▼              ▼               ▼                   ▼
 ┌──────────┐ ┌────────────┐ ┌──────────────┐ ┌────────────────┐
 │ Shipment │ │    Dock    │ │  Disruption  │ │ AI Operations  │
 │ Service  │ │ Scheduling │ │   Recovery   │ │    Service     │
 └────┬─────┘ └─────┬──────┘ └──────┬───────┘ └───────┬────────┘
      │ own DB      │ own DB        │ own DB          │ read-only REST
      ▼             ▼               ▼                 │ calls to others
   Postgres      Postgres        Postgres             │
      │             │               │                 │
      └─────────────┴──── Kafka ────┴─────────────────┘
          (outbox → topic → idempotent consumers)

 Identity: external OIDC provider (Keycloak, proposed) issues JWTs with tenant and role claims.
```

| Service | Owns | Main responsibility |
|---|---|---|
| API Gateway | nothing | Single entry point: routing, token validation, rate limits |
| Shipment | shipments, truck arrivals | Booking, arrival updates, late-arrival detection |
| Dock Scheduling | warehouses, docks, appointments | Deterministic scheduling and conflict validation |
| Disruption Recovery | disruptions, recovery plans, approvals | Plan generation, comparison, human approval |
| AI Operations | conversation logs only | Explains plans using authorized read-only tools |

## 2. Rules

1. **Each service owns its data.** One schema per service and no cross-service joins. Services
   share facts through REST queries or events, never through each other's tables.
2. **REST for questions that need an answer now.** For example, "is this dock free?"
3. **Kafka for facts other services react to later.** For example, `ShipmentArrivalDelayed`
   or `DockOutageReported`.
4. **Events are published through a transactional outbox.** The event row is written in the
   same database transaction as the business change, and a relay publishes it to Kafka.
   This means an event is never lost and never sent for a change that rolled back.
5. **Consumers are idempotent.** Each event carries an ID; consumers record processed IDs, so
   a redelivered event has no effect the second time.
6. **Events are versioned** (`eventType`, `eventVersion`). Consumers ignore fields they don't
   know, so producers can add fields without breaking anyone.
7. **Tenant ID comes from the verified JWT**, never from the request body, and is applied to
   every query.
8. **The scheduler is the only thing that can produce a schedule.** The AI service may explain
   or rank, but any plan goes through the same deterministic validator.

## 3. Cross-cutting concerns

| Concern | How |
|---|---|
| Correlation IDs | `X-Correlation-Id` header, created or validated by `CorrelationIdFilter`, stored in the MDC, passed to downstream calls and events (Phase 1) |
| Errors | RFC 9457 `application/problem+json` from `GlobalExceptionHandler`, never leaking internals (Phase 1) |
| Health | Actuator `/health`, `/health/liveness`, `/health/readiness` (Phase 1) |
| Concurrency | Optimistic locking (`@Version`) on appointments; database constraints against double booking (Phases 2–3) |
| Resilience | Timeouts on every outbound call, circuit breakers with Resilience4j, bounded retries with backoff (Phase 7–8) |
| Tracing and metrics | OpenTelemetry, Prometheus, Grafana (Phase 12) |

## 4. Scheduling engine

`scheduling-engine` is a plain Java library (built in M2), used later by Dock Scheduling and
Disruption Recovery.

```
SchedulingProblem (docks, jobs, crews)
        │
        ▼
 Scheduler ── FcfsScheduler (baseline)            ┐ ListScheduler loop
        │  ├─ PriorityAwareScheduler (iteration) ┘
        │  └─ DispatchScheduler (production): event-driven
        │       all share the Timeline feasibility rules
        ▼
 Schedule ──▶ ScheduleValidator (independent)  ──▶ ScheduleMetrics ──▶ BenchmarkRunner
```

## 5. Build structure

A single Maven multi-module repository. Each service is its own module and its own runnable
jar. `common` holds only infrastructure code, never domain entities, so services stay independent.
