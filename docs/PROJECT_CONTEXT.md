# DockMind AI: Project Context

Read this first when resuming work. It records the actual current state, not the plan.

_Last updated: 2026-10-25, Milestone 8 (of M1–M8; M4 in two parts, M5 in three)._

## Current state

| Item | State |
|---|---|
| Shipped | **M1** `6894a2e` (37 tests) · **M2** `6637873` (336) · **M3** `e6c5e1c` (370) · **M4a** `4744b31` (385: 17 common, 5 common-security, 299 engine, 51 shipment, 13 dock) · **M4b** `0ebe0f6` (393) · **M5a** `c96c83d` (415) · **M5b** `3356005` (424) · **M5c** `ea14625` (433) · **M6** `bbe742c` (438 + 9 web) · **M7** `9fb5862` + `148e746` (440 + 9 web, measured load results). All pushed and confirmed on `origin/main`, 2026-10-25. |
| In progress | **M8 code** (Jenkinsfile, Terraform, image build, cost estimate): written, Terraform formatted; `validate` runs in `ship.sh`. **Not deployed: the developer said to stop before anything touches AWS.** |
| Build machine | Developer Mac: JDK 21.0.12 Temurin, Maven 3.10.0 (wrapper), Docker Desktop |
| Load test (M7, measured on the Mac) | 15 req/s for 2 min through the gateway: p50 8.8 ms, p95 23.7 ms, p99 31.7 ms, 0% errors (`docs/benchmarks/load/RESULTS.md`) |
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
| `disruption-recovery-service` (:8083) | Disruptions, compared plans, approve/reject, append-only audit; applies via Dock Scheduling | `disruption-recovery-service/` |
| `ai-operations-service` (:8084) | Spring AI assistant; read-only tools over Dock Scheduling and Disruption Recovery | `ai-operations-service/` |
| `frontend` (:4200) | Angular 22 web app: login, dashboard, shipments, schedule, disruptions, docks, assistant | `frontend/` |
| `keycloak` (:8180) | Login server; realm as code | `keycloak/realm-dockmind.json` |
| `observability` (Grafana :13000, Prometheus :19090) | grafana/otel-lgtm: collector, Prometheus, Tempo, Loki, Grafana; DockMind dashboard | `observability/` |
| `api-gateway` (:8080, host :18080) | Reactive Spring Cloud Gateway: routing, edge JWT check, Redis rate limit per tenant, timeouts + circuit breakers | `api-gateway/` |

## Commands

```bash
./scripts/run-local.sh                            # run everything, then open http://localhost:4200
./scripts/run-local.sh e2e                        # end-to-end browser test against the running system
./scripts/ship.sh "Mx: ..."                       # format, verify, scan, commit, push, confirm
./mvnw verify                                     # everything (Docker running); also writes docs/benchmarks/
./mvnw verify -DskipBenchmark                     # faster, skips the benchmark run
./mvnw -pl scheduling-engine -am verify           # engine + benchmark only
```

## Known issues and risks

- M5c code hasn't been compiled yet (first use of Spring AI here); the first `ship.sh` may surface fixes.
- A live AI answer needs `OPENAI_API_KEY` in `.env`; without it the assistant returns 503 and everything else works.
- Outages and late arrivals from an applied plan are not stored by Dock Scheduling; a later full `compute` ignores them. Persisting them is planned with M5c/M6.
- Keycloak runs in dev mode (local only); production settings come with M8.
- `/actuator/prometheus` is open to anyone who can reach a service port (all bound to 127.0.0.1 locally). In AWS it must sit on a private network or a separate management port.
- Traces don't yet continue across Kafka (the outbox relay sends later, on another thread).
- `SchedulerPropertyTest` (~98 s) and dispatcher speed at 180 trucks are still optimisation candidates.
- `apply` re-reads pending shipments; if one was cancelled after the plan was made, its plan line fails validation (409), which is the safe outcome.
- Dockerfiles and the `app` Compose profile are not exercised by tests; calling `/api/v1` end to end locally needs Keycloak (M6).
- Published outbox rows are never deleted yet; a retention job is planned for M7.
- Dock opening hours and schedule days are in UTC; per-warehouse time zones are a later improvement.
- `SchedulerPropertyTest` takes ~98 s (dispatcher speed); optimisation planned for M7.
- Scheduler is a greedy heuristic (D15): good and explainable, not optimal.
- Each milestone needs one `ship.sh` run on the Mac (the assistant can't run commands there).

## Next steps

1. `./scripts/ship.sh "M8: Jenkins pipeline and Terraform for AWS (not deployed)"`.
2. **Stop.** Deploying to AWS (~$200/month running, see `docs/AWS_COST_ESTIMATE.md`) needs the
   developer's explicit go-ahead, an AWS account and a budget alert. Steps: `infra/README.md`.
