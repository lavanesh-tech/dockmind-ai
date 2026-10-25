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

## D14. Scheduling engine as a framework-free module (M2)
No Spring and no database: just problem in, schedule out. This makes it fast to test (hundreds
of generated scenarios in seconds), deterministic, and reusable by the Dock Scheduling and
Disruption Recovery services in M4–M5.

## D15. Greedy list scheduling instead of an exact solver (M2)
Exact optimisation (MILP or constraint programming) gives better schedules but adds a heavy
dependency and unpredictable run times. A transparent greedy heuristic is easy to explain to
dispatchers and fast. Both algorithms share the feasibility code, so the benchmark measures only
the decision rules. An exact solver remains a possible later improvement.

## D15b. Event-driven dispatcher as the production scheduler (M2)
The first design (priority-first booking across the day) was measured and found to make
standard trucks wait longer. The dispatcher decides only among arrived trucks and served every
truck at 80% load. Both designs stay in the benchmark so the evidence is reproducible.

## D16. An independent validator is the source of truth (M2)
`ScheduleValidator` shares no code with the schedulers. Every published benchmark schedule, and
from M5 every recovery plan (including anything the AI suggests), must pass it.

## D17. Benchmark results generated during the build (M2)
`verify` runs the benchmark and writes `docs/benchmarks/`, so the committed numbers always come
from the committed code. No hand-typed metrics.

## D18. Provider-agnostic resource server in M3; Keycloak setup moves to M6 (M3)
Services validate standard JWTs (signature, expiry, issuer) from any OIDC provider; tests mint
real RS256 tokens. The Keycloak realm (users, roles, tenant mapper) is first used by a browser
login in M6, so it is built and verified there instead of shipped untested now. Scope unchanged.

## D19. Tenant from the token only (M3)
`TenantId.of(jwt)` is the only source of the tenant. Request bodies have no tenant field, and
unknown JSON fields are ignored, so a client can't target another tenant. Another tenant's
resources return 404, not 403.

## D20. Command endpoints for state changes (M3)
`POST /{id}/arrival` and `POST /{id}/cancel` instead of a generic `PATCH status`: each allowed
transition is explicit, documented and separately securable.

## D21. Transactional outbox for every published event (M4a)
Writing to the database and to Kafka in one step can't be atomic: one can succeed while the
other fails. Writing the event row in the same database transaction, then relaying it, makes
"change happened" and "event will be published" the same fact. The relay uses
`FOR UPDATE SKIP LOCKED`, so several instances can run it safely. Delivery is at least once.

## D22. Read model instead of synchronous calls (M4a)
Dock Scheduling keeps its own `shipment_view` built from events instead of calling the
shipment service at scheduling time. If the shipment service is down, scheduling still works,
and there are no cross-service joins. The cost is eventual consistency: a just-booked shipment
appears in scheduling a moment later.

## D23. Idempotent, stale-safe consumers with a dead-letter topic (M4a)
Deduplicate by `eventId` (processed-event table, same transaction as the update); ignore events
older than the view's last applied event; 3 bounded retries for transient errors; permanent
errors straight to `shipment-events.DLT`. One poison message never blocks the partition.

## D24. M4 shipped in two parts; Disruption Recovery service built with its logic in M5 (M4a)
Smaller reviewable changes, as the brief requires. The Disruption Recovery service is created in
M5 together with the recovery logic it exists for, rather than as an empty skeleton now.

## D25. Shared security code in `common-security`, test helpers in `test-support` (M4a)
Both services read identity the same way, so that code lives in one module. URL rules stay
per service. Test-only code (signed test JWTs, containers) is a test-scoped module that never ships.

## D26. Reactive (WebFlux) gateway (M4b)
A gateway mostly waits on other services. Non-blocking I/O means a slow backend holds no thread,
so one struggling service can't exhaust the gateway. The services stay on Spring MVC, which is
simpler to write and debug for request/response business logic.

## D27. Validate tokens at the edge and again in each service (M4b)
The gateway rejects bad tokens before they cost backend work; services still validate because
they must never trust that traffic came through the gateway (defence in depth). Role rules live
only in the services, next to the endpoints they protect.

## D28. Rate limit per tenant, in Redis (M4b)
A token bucket keyed by the tenant claim: limits are fair between companies, and shared across
gateway instances because the counters live in Redis, not in one instance's memory.

## D29. Timeouts and circuit breakers return 503 problem JSON (M4b)
Every route has a time limit and a Resilience4j circuit breaker. Callers get a fast, explicit
503 with a correlation ID instead of hanging. An open circuit gives a failing service room to recover.

## D30. Recovery plans are full schedules, re-checked where they are applied (M5a)
A recovery plan is a complete proposed schedule. Dock Scheduling validates it again against its
own data before saving, so no planner (a person, the recovery service or, later, the AI) can save
a schedule that breaks a rule. Plans carry the schedule version they were made from; if the
schedule changed since, applying fails with 409 instead of overwriting newer work. Plan overrides
can only make the problem stricter (a truck later, a dock blocked, an unmeetable deadline dropped).

## D31. Two recovery strategies, minimal change first (M5a)
Started appointments stay fixed. MINIMAL_CHANGE keeps every unaffected appointment; FULL_RESCHEDULE
re-plans everything not started. Plans are ranked by urgent trucks left out, then all trucks left
out, then appointments moved, then waiting time: dispatchers and drivers pay for every change, so
fewer moves wins ties. Plans moving more than 3 appointments need an ADMIN (configurable).

## D32. M5 shipped in three parts (M5a)
Engine and apply gate, then the recovery service, then AI, so each change stays reviewable.

## D33. Recovery calls Dock Scheduling with the user's own token (M5b)
The recovery service relays the caller's JWT instead of using a service account, so Dock
Scheduling applies the caller's tenant and role. A compromised or buggy recovery service can do
no more than the person using it. Machine-to-machine credentials come with Keycloak in M6 if needed.

## D34. No remote calls inside database transactions (M5b)
Snapshots are fetched and plans applied outside transactions; results are saved in short
transactions afterwards. A slow dependency then can't hold database connections. Optimistic
locking on the disruption makes concurrent approvals safe; a plan that Dock Scheduling rejects
as stale closes the disruption as FAILED, with the reason in the audit trail.

## D35. The audit trail is append-only in the database itself (M5b)
`audit_entry` has triggers that reject UPDATE, DELETE and TRUNCATE, the entity is `@Immutable`,
and the repository has no update or delete methods. History can't be rewritten by a bug or by
anyone using the application's database credentials.

## D36. Spring AI 2.0.2 with OpenAI, behind a scripted model in tests (M5c)
Spring AI 2.0 is the line built for Spring Boot 4. Tests replace the model with a scripted
`ChatModel` that plays back tool calls and answers, so the build needs no API key, costs nothing
and is deterministic. The real key is read from the environment (local `.env`) only.

## D37. The AI is read-only by construction, not by prompt (M5c)
The assistant's only path to other services is `ReadOnlyBackend`, whose single public method is a
GET; its tools are reads; the user's own token is relayed so the other services enforce tenant and
role. A tool the model asks for but doesn't have is refused (422) and nothing is executed. The
system prompt also says "read-only", but safety doesn't depend on the model obeying it: tests
script a prompt-injected model that tries to approve a plan and prove no request but GETs is sent.

## D38. Keycloak realm as code, verified by a test (M6; supersedes the D9 proposal)
`keycloak/realm-dockmind.json` defines roles, the `tenant_id` claim (an admin-only user attribute,
so users can't change their own tenant), a public browser client (code flow + PKCE S256, no
password grant) and demo users. Passwords and the smoke-test client secret are `${ENV}` placeholders
filled from `.env`, so no credentials are in Git. `KeycloakRealmTest` boots the real Keycloak with
this file on every build and checks the tokens against DockMind's own security code.

## D39. Browser talks only to the gateway, through a same-origin /api proxy (M6)
The Angular dev server proxies `/api` to the gateway, so there is no CORS to configure and the
access token is attached only to `/api/` requests, never to other sites. Containers validate
tokens with issuer `localhost:8180` (what the browser sees) and fetch keys over the Docker network.

## D40. Angular 22, Material and Tailwind utilities; the web app is part of ship.sh (M6)
Angular 22 supports the developer's Node 26. Tailwind is used for layout utilities only (no reset)
so it doesn't fight Material's styles. `ship.sh` builds and unit-tests the web app; the Playwright
end-to-end workflow test needs the whole system running, so it is `run-local.sh e2e`.

## D9. External OIDC provider, proposed Keycloak (proposed; decide in Phase 6)
Writing an authorization server from scratch is risky and not the project's focus. A standard
provider issues JWTs, and the services validate them as OAuth2 resource servers.
