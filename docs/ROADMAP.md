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
| M2 | Scheduling engine and benchmark | 3, 4 | Pure-Java deterministic scheduler (dock capability, arrival windows, loading time, priority, capacity); FCFS baseline; seeded workload generator; CSV/JSON report (avg/p95 wait, utilization, conflicts, compute time); `BENCHMARKS.md` from real runs only | — | **Code complete, awaiting `ship.sh`** |
| M3 | APIs, security and multi-tenancy | 5, 6 | REST APIs with Bean Validation and OpenAPI; contract tests; OIDC provider (Keycloak in Docker); JWT validation; ADMIN/DISPATCHER/VIEWER; tests proving cross-tenant access fails | — | Not started |
| M4 | Microservices, gateway and events | 7, 8 | Dock Scheduling and Disruption Recovery services with their own databases; Spring Cloud Gateway; Redis rate limiting; timeouts and circuit breakers; Kafka with transactional outbox, idempotent consumers, bounded retries, dead-letter topics, versioned events; duplicate-delivery tests | — | Not started |
| M5 | Disruption recovery and Spring AI | 9, 10 | Late-arrival and dock-outage simulation; recovery plan generation and comparison; human approval; immutable audit trail; AI Operations service with authorized read-only tools; tests proving AI cannot change schedules | OpenAI API key, stored in a local `.env` (never pasted in chat) | Not started |
| M6 | Angular frontend | 11 | Dashboard, booking forms, schedule view, recovery approval, charts; Angular Material + Tailwind; unit tests; end-to-end workflow test | Node.js installed (one command) | Not started |
| M7 | Observability and performance | 12 | OpenTelemetry tracing; Prometheus metrics; Grafana dashboard; k6 load test with measured p50/p95/p99; Kafka throughput and recovery-time measurements | — | Not started |
| M8 | CI/CD and AWS deployment | 13 | Jenkins pipeline; images to ECR; Terraform for ECS, RDS, S3; secrets in AWS Secrets Manager; Ansible only where needed; deployment verified | AWS account; **explicit approval of costs before anything is created** | Not started |

**About 8 `ship.sh` runs in total**, plus the one-time items in the "Needs from you" column.
