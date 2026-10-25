# DockMind AI

A multi-tenant warehouse dock scheduling and disruption recovery platform, built in Java 21
and Spring Boot 4.

Logistics operators register warehouses and loading docks, book shipments and truck arrivals,
and get a constraint-aware schedule. When a truck is late or a dock goes down, DockMind
computes feasible recovery plans, explains them, and asks a human to approve significant
changes. Every decision is recorded in an audit trail.

> **Status:** Milestone 6 (login and web app). Benchmark results:
> [docs/benchmarks/RESULTS.md](docs/benchmarks/RESULTS.md). See [ROADMAP.md](docs/ROADMAP.md) for
> what is built and what is next. All data is synthetic, and any benchmark numbers come from
> seeded simulations, not real customers.

## Quick start

Requirements: JDK 21+, Node.js 22.22+/24/26, Docker Desktop.

```bash
./scripts/run-local.sh       # builds, starts every service, Keycloak and the web app
# open http://localhost:4200 and sign in as admin.acme, dispatcher.acme, viewer.acme or dispatcher.globex
# (password: DEMO_USER_PASSWORD in .env, generated on first run)
./scripts/run-local.sh stop  # stop everything
./mvnw verify                # all backend tests (Docker must be running)
```

API docs: run the service, then open `http://localhost:8081/swagger-ui.html`. Every
`/api/v1` call needs a bearer token with a `tenant_id` claim and a DockMind role.

To ship a milestone: `./scripts/ship.sh "Mx: description"`. This formats the code, runs every
test, scans for secrets, commits as the sole author, pushes and confirms GitHub has the commit.

## Repository layout

| Path | What it is |
|---|---|
| `common/` | Shared web infrastructure: correlation IDs, RFC 9457 error responses |
| `common-security/` | JWT roles, tenant from token, 401/403 problem JSON |
| `scheduling-engine/` | Deterministic dock scheduler, FCFS baseline, independent validator, seeded benchmark |
| `dock-scheduling-service/` | Docks, shipment read model from Kafka events, validated schedules (:8082) |
| `disruption-recovery-service/` | Disruptions, compared recovery plans, human approval, append-only audit (:8083) |
| `ai-operations-service/` | Spring AI operations assistant with read-only tools; cannot change anything (:8084) |
| `test-support/` | Signed test JWTs and Testcontainers (test scope only) |
| `shipment-service/` | Shipments REST API and transactional outbox to Kafka (:8081) |
| `api-gateway/` | Single entry point (:8080): routing, edge auth, per-tenant rate limits, circuit breakers |
| `frontend/` | Angular 22 web app (Material + Tailwind), OIDC login with PKCE (:4200) |
| `keycloak/` | Keycloak realm as code: roles, tenant claim, demo users |
| `docs/` | Product spec, architecture, roadmap, decisions, learning guide |

## Documentation

- [Product spec](docs/PRODUCT_SPEC.md): what DockMind does and for whom
- [Architecture](docs/ARCHITECTURE.md): services, data ownership, events
- [Roadmap](docs/ROADMAP.md): the 13 phases and their acceptance criteria
- [Decisions](docs/DECISIONS.md): why things are built the way they are
- [Project context](docs/PROJECT_CONTEXT.md): current state and next steps
- [Learning guide](docs/LEARNING_GUIDE.md): beginner-friendly explanations and interview questions
