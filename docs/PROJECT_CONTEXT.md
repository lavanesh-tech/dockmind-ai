# DockMind AI: Project Context

Read this first when resuming work. It records the actual current state, not the plan.

_Last updated: 2026-10-25, Milestone 1 of 8._

## Current state

| Item | State |
|---|---|
| Milestone | **M1: Foundation and persistence**, code complete |
| Last verified build | 2026-10-25 on the developer Mac (JDK 21.0.12 Temurin, Maven 3.10.0): `./mvnw verify` **BUILD SUCCESS**, 21 tests, 0 failures. That build covered the Phase 1 code only. |
| Not yet verified | Persistence code (JPA, Flyway, Testcontainers tests) and the Spotless formatting gate. Verified by the first `./scripts/ship.sh` run. |
| Git | Remote `origin` = `https://github.com/lavanesh-tech/dockmind-ai.git`. **No commits yet.** |
| Verified metrics | None yet (benchmarks start in M2). |

## Implemented

| Area | Location |
|---|---|
| Root build: Boot 4.1.1, Spring Cloud 2025.1.3 BOM, enforcer, Spotless | `pom.xml` |
| One-command verify/commit/push | `scripts/ship.sh` |
| Correlation IDs, RFC 9457 errors | `common/src/main/java/com/dockmind/common/web/` |
| Shipment domain: entity, enums, tenant-scoped repository | `shipment-service/src/main/java/com/dockmind/shipment/domain/` |
| Schema | `shipment-service/src/main/resources/db/migration/V1__create_shipment.sql` |
| Local PostgreSQL | `docker-compose.yml`, `.env.example` |
| Tests | `common/src/test/...` (unit), `shipment-service/src/test/...` (unit, HTTP integration, Testcontainers repository) |

## Not implemented yet

M2–M8: scheduler and benchmark, REST APIs and security, the other services, gateway, Kafka,
recovery, Spring AI, Angular, observability, CI/CD and AWS.

## Commands

```bash
./scripts/ship.sh "M1: ..."                     # format, verify, scan secrets, commit, push, confirm
./mvnw verify                                   # build + all tests (Docker must be running)
./mvnw spotless:apply                           # fix formatting
cp .env.example .env && docker compose up -d    # local PostgreSQL for running the app
./mvnw -pl shipment-service spring-boot:run     # run on :8081 (needs DB_PASSWORD in the environment)
```

## Known issues and risks

- Persistence code has not been through a real build yet; the first `ship.sh` may surface small fixes.
- Automation limit: the assistant cannot run commands on the developer Mac (computer use can't
  type into Terminal), so each milestone needs one `ship.sh` run by the developer.

## Next steps

1. Run `./scripts/ship.sh "M1: foundation and persistence"` and fix anything it reports.
2. After it prints `SHIPPED`, start M2: the scheduling engine and the FCFS benchmark.
