# DockMind AI: Decision Log

Short records of decisions and why they were made. New decisions are added at the bottom;
old ones are never edited, only superseded by a later entry.

## D1. Spring Boot 4.1.1 with Spring Cloud 2025.1.3 (Phase 1)
Spring Boot 4.1 is the newest supported line (open-source support until about July 2027);
3.5 reached end of open-source support in June 2026. Spring Cloud 2025.1.x is documented as
compatible with Boot 4.1.x. Java 21 is the LTS baseline.

## D2. One Maven multi-module repository (Phase 1)
Services live in one repository so a reviewer can clone and build everything with one command.
Each service is still a separate module and a separate runnable jar with its own database.

## D3. `common` holds infrastructure only (Phase 1)
Sharing domain entities between services couples them to one database model, which defeats the
point of microservices. `common` only contains code that is the same everywhere: correlation IDs
and error handling.

## D4. Validate client-supplied correlation IDs (Phase 1)
Incoming IDs are reused only if they match `[A-Za-z0-9._-]{1,64}`. Otherwise a new UUID is
generated. This prevents log injection (newlines forging log lines) and unbounded header values.

## D5. RFC 9457 problem details for all errors (Phase 1)
A standard, documented error shape (`application/problem+json`) that clients can parse. Responses
include a correlation ID and never include stack traces, SQL or parser messages.

## D6. Build the scheduler before the distributed plumbing (Phase 1, planning)
The deterministic scheduler and its benchmark are the core differentiator. Building them in
Phases 3–4 means the most valuable part exists early and is not blocked by Kafka or cloud work.

## D7. No Kubernetes (Phase 1, planning)
AWS ECS covers the deployment needs. Kubernetes would add complexity without demonstrating
anything the project needs.

## D8. Sole authorship (Phase 1)
All commits are authored by Lavanesh Thirukonda Mahendran. AI assistance is used for drafting
and review; no co-author trailers or other identities are added to commits.

## D10. 13 phases regrouped into 8 milestones (M1)
Fewer, larger verified increments, to reduce repetitive manual work. Scope is unchanged.

## D11. One-command shipping with `scripts/ship.sh` (M1)
Every milestone goes through the same gates: formatting, the full build and tests, a secret
scan, an authorship check, a push, and confirmation that GitHub has the commit. A failure at
any gate pushes nothing.

## D12. Flyway owns the schema; Hibernate only validates (M1)
`ddl-auto: validate` means schema changes are explicit, reviewed SQL migrations. Generating
the schema from entities would hide changes and can't be rolled out safely to production.

## D13. Testcontainers instead of H2 for database tests (M1)
Tests run against the same PostgreSQL version as production, so constraints, `TIMESTAMPTZ`
and SQL behave the same. H2 would pass tests that fail in production.

## D9. External OIDC provider, proposed Keycloak (proposed; decide in Phase 6)
Writing an authorization server from scratch is risky and not the project's focus. A standard
provider issues JWTs, and the services validate them as OAuth2 resource servers.
