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
| M4a | Events and the Dock Scheduling service | 7, 8 | Shared `common-security`; transactional outbox in shipment-service publishing versioned events to Kafka; Dock Scheduling service with its own DB, a shipment read model built from events, idempotent and stale-safe consumer, bounded retries, dead-letter topic; docks API; schedule computation with the engine, validator gate and history; Testcontainers Kafka tests | — | **Code complete, awaiting `ship.sh`** |
| M4b | Gateway and resilience | 7 | Spring Cloud Gateway in front of both services; JWT check at the edge; Redis rate limiting per tenant; timeouts and circuit breakers with problem-JSON fallbacks; Docker Compose for the whole system | — | Not started |
| M5 | Disruption recovery and Spring AI | 9, 10 | Disruption Recovery service with its own DB (moved from M4, see D24); late-arrival and dock-outage simulation; recovery plan generation and comparison; human approval; immutable audit trail; AI Operations service with authorized read-only tools; tests proving AI cannot change schedules | OpenAI API key, stored in a local `.env` (never pasted in chat) | Not started |
| M6 | Angular frontend and login | 11 | Keycloak in Docker Compose (realm, roles, tenant claim; moved from M3, see D18); OIDC login with PKCE; dashboard, booking forms, schedule view, recovery approval, charts; Angular Material + Tailwind; unit tests; end-to-end workflow test | Node.js installed (one command) | Not started |
| M7 | Observability and performance | 12 | OpenTelemetry tracing; Prometheus metrics; Grafana dashboard; k6 load test with measured p50/p95/p99; Kafka throughput and recovery-time measurements | — | Not started |
| M8 | CI/CD and AWS deployment | 13 | Jenkins pipeline; images to ECR; Terraform for ECS, RDS, S3; secrets in AWS Secrets Manager; Ansible only where needed; deployment verified | AWS account; **explicit approval of costs before anything is created** | Not started |

**About 9 `ship.sh` runs in total** (M4 ships in two reviewable parts), plus the one-time items in the "Needs from you" column.
