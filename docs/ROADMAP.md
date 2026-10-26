# DockMind AI: Roadmap (8 milestones)

The original 13 phases are grouped into 8 milestones. Nothing was removed. Every technology,
business requirement and quality gate from the [master prompt](MASTER_PROMPT.md) is still here.

**How a milestone ships:** the code is written into the repository, then one command,
`./scripts/ship.sh "Mx: ..."`, formats the code, runs the full build and all tests, scans for
secrets, checks sole authorship, commits, pushes and confirms GitHub received the commit. If
anything fails, nothing is pushed.

| # | Milestone | Was phases | Acceptance criteria | Needs from you | Status |
|---|---|---|---|---|---|
| M1 | Foundation and persistence | 1, 2 | Multi-module build; correlation IDs; RFC 9457 errors; health/readiness; PostgreSQL via Docker Compose; Flyway; JPA `Shipment` with tenant-scoped queries, DB constraints and optimistic locking; Testcontainers tests; Spotless formatting | Docker Desktop running | ✅ **Shipped** 2026-10-25, 37 tests, commit `6894a2e` |
| M2 | Scheduling engine and benchmark | 3, 4 | Pure-Java deterministic scheduler (dock capability, arrival windows, loading time, priority, capacity); FCFS baseline; seeded workload generator; CSV/JSON report (avg/p95 wait, utilization, conflicts, compute time); `BENCHMARKS.md` from real runs only | — | ✅ **Shipped** 2026-10-25, 336 tests, commit `6637873` |
| M3 | APIs, security and multi-tenancy | 5, 6 | REST APIs with Bean Validation and OpenAPI; contract tests; JWT resource server (any OIDC provider); ADMIN/DISPATCHER/VIEWER; tenant from token; tests with real signed JWTs proving expired/forged/foreign tokens and cross-tenant access fail | — | ✅ **Shipped** 2026-10-25, 370 tests, commit `e6c5e1c` |
| M4a | Events and the Dock Scheduling service | 7, 8 | Shared `common-security`; transactional outbox in shipment-service publishing versioned events to Kafka; Dock Scheduling service with its own DB, a shipment read model built from events, idempotent and stale-safe consumer, bounded retries, dead-letter topic; docks API; schedule computation with the engine, validator gate and history; Testcontainers Kafka tests | — | ✅ **Shipped** 2026-10-25, 385 tests, commit `4744b31` |
| M4b | Gateway and resilience | 7 | Spring Cloud Gateway in front of both services; JWT check at the edge; Redis rate limiting per tenant; timeouts and circuit breakers with problem-JSON fallbacks; Dockerfiles and Docker Compose for the whole system | — | ✅ **Shipped** 2026-10-25, 393 tests, commit `0ebe0f6` |
| M5a | Recovery planning | 9 | Engine: fixed appointments, late-arrival and dock-outage disruptions, minimal-change vs full-reschedule plans, ranked and validated; recovery benchmark in `RESULTS.md`; Dock Scheduling `snapshot` and `apply` endpoints that re-validate every plan, reject stale plans (409) and require ADMIN for large changes | — | ✅ **Shipped** 2026-10-25, 415 tests, commit `c96c83d` |
| M5b | Disruption Recovery service | 9 | Own service and DB (D24); reports disruptions, fetches the snapshot with token relay, timeout and circuit breaker; stores and compares plans; human approve/reject; append-only audit trail enforced by the database | — | ✅ **Shipped** 2026-10-25, 424 tests, commit `3356005` |
| M5c | AI Operations (Spring AI) | 10 | AI Operations service with authorized read-only tools; explanations of plans; tests proving AI cannot change schedules | OpenAI API key, stored in a local `.env` (never pasted in chat) | ✅ **Shipped** 2026-10-25, 433 tests, commit `ea14625` |
| M6 | Angular frontend and login | 11 | Keycloak in Docker Compose (realm, roles, tenant claim; moved from M3, see D18); OIDC login with PKCE; dashboard, booking forms, schedule view, recovery approval, charts; Angular Material + Tailwind; unit tests; end-to-end workflow test | Node.js installed (one command) | ✅ **Shipped** 2026-10-25, 438 tests + 9 web tests, commit `bbe742c` |
| M7 | Observability and performance | 12 | OpenTelemetry tracing; Prometheus metrics; Grafana dashboard; k6 load test with measured p50/p95/p99; Kafka throughput and recovery-time measurements | — | **Code complete, awaiting `ship.sh`** |
| M8 | CI/CD and AWS deployment | 13 | Jenkins pipeline; images to ECR; Terraform for ECS, RDS, S3; secrets in AWS Secrets Manager; Ansible only where needed; deployment verified | AWS account; **explicit approval of costs before anything is created** | **Paused at the developer's request**: nothing in AWS until explicitly approved |

**11 `ship.sh` runs in total** (M4 in two parts, M5 in three), plus the one-time items in the "Needs from you" column.
