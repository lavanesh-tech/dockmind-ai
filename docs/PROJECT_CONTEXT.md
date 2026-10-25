# DockMind AI: Project Context

Read this first when resuming work. It records the actual current state, not the plan.

_Last updated: 2026-10-25, Milestone 4a (of M1–M8, with M4 in two parts)._

## Current state

| Item | State |
|---|---|
| Shipped | **M1** `6894a2e` (37 tests) · **M2** `6637873` (336) · **M3** `e6c5e1c` (370: 17 common, 299 engine, 54 shipment). All pushed and confirmed on `origin/main`, 2026-10-25. |
| In progress | **M4a events and Dock Scheduling**: code complete, **not yet built**. Verified by the next `ship.sh`. |
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

## Commands

```bash
./scripts/ship.sh "Mx: ..."                       # format, verify, scan, commit, push, confirm
./mvnw verify                                     # everything (Docker running); also writes docs/benchmarks/
./mvnw verify -DskipBenchmark                     # faster, skips the benchmark run
./mvnw -pl scheduling-engine -am verify           # engine + benchmark only
```

## Known issues and risks

- M4a code hasn't been compiled yet; the first `ship.sh` may surface fixes.
- Published outbox rows are never deleted yet; a retention job is planned for M7.
- Dock opening hours and schedule days are in UTC; per-warehouse time zones are a later improvement.
- `SchedulerPropertyTest` takes ~98 s (dispatcher speed); optimisation planned for M7.
- Scheduler is a greedy heuristic (D15): good and explainable, not optimal.
- Each milestone needs one `ship.sh` run on the Mac (the assistant can't run commands there).

## Next steps

1. `./scripts/ship.sh "M4a: transactional outbox, Kafka events and dock scheduling service"`.
2. Then M4b: Spring Cloud Gateway, Redis rate limiting, circuit breakers, full Docker Compose.
