# DockMind AI: Project Context

Read this first when resuming work. It records the actual current state, not the plan.

_Last updated: 2026-10-25, Milestone 4b (of M1–M8, with M4 in two parts)._

## Current state

| Item | State |
|---|---|
| Shipped | **M1** `6894a2e` (37 tests) · **M2** `6637873` (336) · **M3** `e6c5e1c` (370) · **M4a** `4744b31` (385: 17 common, 5 common-security, 299 engine, 51 shipment, 13 dock). All pushed and confirmed on `origin/main`, 2026-10-25. |
| In progress | **M4b gateway and resilience**: code complete, **not yet built**. Verified by the next `ship.sh`. |
| Build machine | Developer Mac: JDK 21.0.12 Temurin, Maven 3.10.0 (wrapper), Docker Desktop |
| Verified metrics | `docs/benchmarks/RESULTS.md` (M2, generated on the Mac build; identical to the cloud run). At 140 trucks/day: dispatcher 0.0 unscheduled vs FCFS 10.4; urgent avg wait 6.7 vs 37.6 min. Dispatcher compute 469 ms at 180 trucks (an optimisation target for M7). |

## Modules

| Module | Purpose | Location |
|---|---|---|
| `common` | Correlation IDs, RFC 9457 errors | `common/` |
| `scheduling-engine` | Model, FCFS + priority-aware schedulers, validator, metrics, seeded benchmark | `scheduling-engine/` |
| `common-security` | JWT roles, tenant from token, 401/403 problem JSON | `common-security/` |
| `test-support` | Signed test JWTs, Postgres + Kafka Testcontainers (test scope only) | `test-support/` |
| `shipment-service` (:8081) | Shipments, REST API, JWT security, transactional outbox → Kafka `shipment-events` | `shipment-service/` |
| `dock-scheduling-service` (:8082) | Docks, shipment read model from events, schedule computation + history | `dock-scheduling-service/` |
| `api-gateway` (:8080) | Reactive Spring Cloud Gateway: routing, edge JWT check, Redis rate limit per tenant, timeouts + circuit breakers | `api-gateway/` |

## Commands

```bash
./scripts/ship.sh "Mx: ..."                       # format, verify, scan, commit, push, confirm
./mvnw verify                                     # everything (Docker running); also writes docs/benchmarks/
./mvnw verify -DskipBenchmark                     # faster, skips the benchmark run
./mvnw -pl scheduling-engine -am verify           # engine + benchmark only
```

## Known issues and risks

- M4b code hasn't been compiled yet; the first `ship.sh` may surface fixes.
- Dockerfiles and the `app` Compose profile are not exercised by tests; calling `/api/v1` end to end locally needs Keycloak (M6).
- Published outbox rows are never deleted yet; a retention job is planned for M7.
- Dock opening hours and schedule days are in UTC; per-warehouse time zones are a later improvement.
- `SchedulerPropertyTest` takes ~98 s (dispatcher speed); optimisation planned for M7.
- Scheduler is a greedy heuristic (D15): good and explainable, not optimal.
- Each milestone needs one `ship.sh` run on the Mac (the assistant can't run commands there).

## Next steps

1. `./scripts/ship.sh "M4b: API gateway with rate limiting and circuit breakers"`.
2. Then M5: Disruption Recovery service and Spring AI (needs an OpenAI API key in a local `.env`).
