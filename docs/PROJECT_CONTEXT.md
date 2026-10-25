# DockMind AI: Project Context

Read this first when resuming work. It records the actual current state, not the plan.

_Last updated: 2026-10-25, Milestone 3 of 8._

## Current state

| Item | State |
|---|---|
| Shipped | **M1** `6894a2e` (37 tests) · **M2** `6637873` (336 tests: 15 common, 299 scheduling-engine, 22 shipment-service). Both pushed and confirmed on `origin/main`, 2026-10-25. |
| In progress | **M3 APIs, security and multi-tenancy**: code complete, **not yet built**. Verified by the next `ship.sh`. |
| Build machine | Developer Mac: JDK 21.0.12 Temurin, Maven 3.10.0 (wrapper), Docker Desktop |
| Verified metrics | `docs/benchmarks/RESULTS.md` (M2, generated on the Mac build; identical to the cloud run). At 140 trucks/day: dispatcher 0.0 unscheduled vs FCFS 10.4; urgent avg wait 6.7 vs 37.6 min. Dispatcher compute 469 ms at 180 trucks (an optimisation target for M7). |

## Modules

| Module | Purpose | Location |
|---|---|---|
| `common` | Correlation IDs, RFC 9457 errors | `common/` |
| `scheduling-engine` | Model, FCFS + priority-aware schedulers, validator, metrics, seeded benchmark | `scheduling-engine/` |
| `shipment-service` | Shipment entity, Flyway schema, REST API (`/api/v1/shipments`), JWT security, roles, OpenAPI | `shipment-service/` |

## Commands

```bash
./scripts/ship.sh "Mx: ..."                       # format, verify, scan, commit, push, confirm
./mvnw verify                                     # everything (Docker running); also writes docs/benchmarks/
./mvnw verify -DskipBenchmark                     # faster, skips the benchmark run
./mvnw -pl scheduling-engine -am verify           # engine + benchmark only
```

## Known issues and risks

- M3 code hasn't been compiled yet (needs Spring jars that only the Mac can download); the first `ship.sh` may surface fixes.
- Scheduler is a greedy heuristic (D15): good and explainable, not optimal.
- Each milestone needs one `ship.sh` run on the Mac (the assistant can't run commands there).

## Next steps

1. `./scripts/ship.sh "M3: secured multi-tenant shipment API"`.
2. Then M4: Dock Scheduling and Disruption Recovery services, Spring Cloud Gateway, Redis rate limiting, Kafka outbox.
