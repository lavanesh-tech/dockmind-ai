# DockMind AI: Project Context

Read this first when resuming work. It records the actual current state, not the plan.

_Last updated: 2026-10-25, Milestone 2 of 8._

## Current state

| Item | State |
|---|---|
| Last shipped | **M1 Foundation and persistence**: commit `6894a2ea5635a897da5493d0fb78de9aa57b969f`, pushed and confirmed on `origin/main` on 2026-10-25. 37 tests passed (15 common, 22 shipment-service). |
| In progress | **M2 Scheduling engine and benchmark**: code complete, **not yet built**. Verified by the next `ship.sh` run. |
| Build machine | Developer Mac: JDK 21.0.12 Temurin, Maven 3.10.0 (wrapper), Docker Desktop |
| Verified metrics | None yet. `docs/benchmarks/` is created by the first M2 build. |

## Modules

| Module | Purpose | Location |
|---|---|---|
| `common` | Correlation IDs, RFC 9457 errors | `common/` |
| `scheduling-engine` | Model, FCFS + priority-aware schedulers, validator, metrics, seeded benchmark | `scheduling-engine/` |
| `shipment-service` | Shipment entity, Flyway schema, tenant-scoped repository, health | `shipment-service/` |

## Commands

```bash
./scripts/ship.sh "Mx: ..."                       # format, verify, scan, commit, push, confirm
./mvnw verify                                     # everything (Docker running); also writes docs/benchmarks/
./mvnw verify -DskipBenchmark                     # faster, skips the benchmark run
./mvnw -pl scheduling-engine -am verify           # engine + benchmark only
```

## Known issues and risks

- M2 code hasn't been compiled yet; the first `ship.sh` may surface small fixes.
- Scheduler is a greedy heuristic (D15): good and explainable, not optimal.
- Each milestone needs one `ship.sh` run on the Mac (the assistant can't run commands there).

## Next steps

1. `./scripts/ship.sh "M2: deterministic scheduling engine and FCFS benchmark"`.
2. Then M3: REST APIs with OpenAPI, Keycloak OIDC, JWT, roles, tenant isolation tests.
