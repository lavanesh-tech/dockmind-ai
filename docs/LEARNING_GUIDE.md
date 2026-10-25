# DockMind AI: Learning Guide

Plain-language explanations of everything in the codebase, written so you can study the project
(for example with ChatGPT) and explain every line in an interview. Each phase adds a section.

---

## Phase 1: Repository foundation

### 1.1 Maven multi-module builds

`pom.xml` at the root has `<packaging>pom</packaging>`. It produces no jar itself; it lists the
modules (`common`, `shipment-service`) and shares configuration with them.

- **Parent POM** (`spring-boot-starter-parent`): supplies tested, compatible versions for
  hundreds of libraries, plus sensible plugin defaults (Java version, `-parameters`, test runner).
  That's why most dependencies in the module POMs have no `<version>`.
- **BOM import** (`spring-cloud-dependencies` with `<scope>import</scope>`): pulls in a second
  version list, for Spring Cloud. A BOM ("bill of materials") only manages versions; it adds no
  dependencies by itself.
- **`dependencyManagement` vs `dependencies`:** the first says "if anyone uses X, use this
  version"; the second actually adds X to the classpath.
- **Reactor order:** Maven builds `common` before `shipment-service` because the service depends on it.
- **Enforcer plugin:** fails the build immediately with a clear message on the wrong JDK or Maven,
  instead of a confusing compiler error later.
- **`provided` scope** (servlet API in `common`): needed to compile, but supplied at runtime by
  Tomcat, so it isn't packaged twice.

**Try it:** `./mvnw -pl common test` builds and tests only the `common` module.

### 1.2 Spring Boot startup and dependency injection

- `@SpringBootApplication` = `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`.
  Component scanning covers `com.dockmind.shipment` and its sub-packages only.
- **Auto-configuration** looks at the classpath and configures things for you. Tomcat is present,
  so a web server starts; Actuator is present, so `/actuator/health` exists.
- `@Import(CommonWebConfiguration.class)` adds beans from a package that scanning doesn't reach.
  This is explicit: you can see in one line where the shared beans come from.
- `@Bean` methods create objects that Spring manages. Spring Boot automatically registers any
  `Filter` bean with Tomcat.
- `@Configuration(proxyBeanMethods = false)` is an optimisation, used when `@Bean` methods don't
  call each other.

### 1.3 Servlet filters and the correlation ID

A **filter** runs around every HTTP request, before the controller and after it.
`CorrelationIdFilter` extends Spring's `OncePerRequestFilter`, which guarantees it runs once per
request even when the request is internally forwarded (for example to an error page).

What it does, in order:
1. Reads `X-Correlation-Id`. It reuses the value **only if** it matches a strict pattern;
   otherwise it generates a UUID.
2. Puts the ID in the **MDC**, sets a request attribute, and adds the response header.
3. Calls the rest of the chain.
4. In `finally`, removes the ID from the MDC.

**Why validate the header?** It comes from the client, so it is untrusted input. Without the
check, an ID like `abc\nINFO payment approved` would write a fake line into your logs
(*log injection*).

**What is the MDC?** SLF4J's "Mapped Diagnostic Context": a per-thread map that log patterns can
read. `%X{correlationId}` in `application.yml` prints it on every log line.

**Why `finally`?** The MDC is stored in a `ThreadLocal`. Tomcat reuses threads from a pool. If
you don't clear it, the next request on that thread logs with the previous request's ID, even
when the previous request threw an exception. The test `mdcIsClearedEvenWhenDownstreamThrows`
proves this case.

**Why `Ordered.HIGHEST_PRECEDENCE`?** So the ID exists before any other filter runs, including
security filters added later.

### 1.4 Error handling with RFC 9457 problem details

`@RestControllerAdvice` makes `GlobalExceptionHandler` apply to every controller.
Each `@ExceptionHandler` method maps one exception type to a response.

Responses use the standard `application/problem+json` shape:
```json
{
  "type": "urn:dockmind:problem:validation",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/shipments",
  "correlationId": "7f3a...",
  "errors": [{ "field": "palletCount", "message": "must be greater than 0" }]
}
```

Security rules applied:
- **Never return internals.** No stack traces, SQL or parser messages. They help attackers and
  confuse users. Log them server-side instead.
- **Always return a correlation ID**, so a user can say "request 7f3a failed" and you can find
  every log line for it.
- **Same 404 for "missing" and "belongs to another tenant".** Otherwise an attacker could probe
  which IDs exist (`ResourceNotFoundException`).
- **Keep framework statuses.** Spring's own errors (unknown URL 404, wrong method 405) implement
  `ErrorResponse`. The catch-all handler keeps their status instead of turning them into 500.

### 1.5 Actuator, liveness and readiness

- `/actuator/health`: overall health.
- `/actuator/health/liveness`: "is the process alive?" If this fails, the platform restarts the container.
- `/actuator/health/readiness`: "can it take traffic right now?" If this fails, the load balancer
  stops sending requests but doesn't restart anything. For example, while the database is down.
- Only `health` and `info` are exposed. Endpoints like `/env` and `/beans` reveal configuration
  and are hidden. A test proves they return 404.

### 1.6 How Phase 1 is tested

| Test | Type | Why this type |
|---|---|---|
| `CorrelationIdFilterTest` | Unit test with Spring's mock request/response | Fast; tests the filter logic alone, including edge cases (log injection, exceptions) |
| `GlobalExceptionHandlerTest` | Unit test calling handler methods directly | Checks the exact JSON fields without starting a server |
| `ShipmentServiceApplicationTest` | Integration test: real server on a random port, real HTTP calls | Proves the wiring: auto-configuration, filter registration, actuator exposure, 404 shape |

The *testing pyramid*: many fast unit tests, fewer slower integration tests, and a few end-to-end
tests (added in Phase 11).

`@ParameterizedTest` runs one test method with several inputs, here five kinds of bad
correlation IDs.

### 1.7 Interview questions for Phase 1

1. Why does `common` contain no domain entities? *(Shared entities couple services to one data model.)*
2. What happens if you forget to clear the MDC? *(Thread reuse leaks the ID into the next request's logs.)*
3. Liveness vs readiness: what's the difference, and what does the platform do when each fails?
4. Why not return the exception message to the client in a 500?
5. What does a BOM do, and how is `dependencyManagement` different from `dependencies`?
6. Why validate a header that "only" goes into logs?
7. What does `OncePerRequestFilter` guarantee that a plain `Filter` doesn't?

---

## Milestone 1 (continued): Persistence

### 1.8 How a row gets into PostgreSQL

`ShipmentRepository.save(shipment)`
→ Spring Data's generated implementation
→ JPA `EntityManager.persist`
→ Hibernate builds the SQL `INSERT`
→ the JDBC driver
→ PostgreSQL.

You write the interface; Spring Data writes the implementation from method names like
`findByIdAndTenantId`.

### 1.9 Flyway migrations

`V1__create_shipment.sql` runs once, the first time the app starts against a database. Flyway
records it in the `flyway_schema_history` table. Rules:
- **Never edit a migration that has already run.** Add `V2__...` instead.
- Hibernate runs with `ddl-auto: validate`. It checks that the entities match the tables and
  refuses to start if they don't, but it never changes the schema itself.

### 1.10 Entity design choices in `Shipment`

- **Invariants in the constructor** (1–60 pallets, the window must move forward): an invalid
  shipment cannot exist in memory.
- **The same rules as database CHECK constraints:** a second line of defence if someone
  bypasses Java, for example with a manual SQL insert. A test proves the database rejects it.
- **State transitions as methods** (`markArrived`, `cancel`) instead of a public `setStatus`.
  Illegal moves throw `IllegalStateException`.
- **`@ElementCollection`** stores the capability set in its own table (`shipment_required_capability`).
- **`equals` by ID, constant `hashCode`:** the recommended pattern for JPA entities whose ID
  never changes once assigned.
- **Protected no-arg constructor:** JPA needs one to create objects when loading rows.

### 1.11 Optimistic locking

The `version` column starts at 0, and every update runs:
`UPDATE ... SET version = version + 1 WHERE id = ? AND version = ?`.
If someone else updated the row first, zero rows match. Hibernate then throws, and Spring turns
that into `ObjectOptimisticLockingFailureException`. Nothing is silently overwritten.

*Optimistic* means it assumes conflicts are rare and checks at save time. *Pessimistic* locking
(`SELECT ... FOR UPDATE`) blocks other writers up front instead. The test
`concurrentUpdatesAreDetectedByOptimisticLocking` acts out two dispatchers editing the same shipment.

### 1.12 Multi-tenancy at the data layer

Every row has `tenant_id`, and every query in application code includes it
(`findByIdAndTenantId`). A request for another tenant's shipment returns "not found", never
"forbidden", so the caller can't learn that the ID exists. In M3, the tenant ID will come from
the verified JWT, never from the request body.

### 1.13 Testcontainers

`TestcontainersConfiguration` starts a throwaway PostgreSQL 17 in Docker. `@ServiceConnection`
wires the datasource to it automatically. Why not an in-memory H2? H2 is a different database
with different SQL, types and constraints, so tests could pass on H2 and fail on PostgreSQL.

### 1.14 The quality gates in `ship.sh`

Format (Spotless) → build and tests → secret scan → authorship check → commit → push → confirm
GitHub's `main` equals the local commit. Each gate stops everything on failure. This mirrors
what a CI pipeline does, and in M8 Jenkins runs the same checks on every push.

### 1.15 More interview questions

8. What does `ddl-auto: validate` do, and why not `update` in production?
9. Explain optimistic vs pessimistic locking. When would you choose each?
10. Why test with a real PostgreSQL instead of H2?
11. Why return 404 rather than 403 for another tenant's resource?
12. Why put validation in both the entity and the database?

---

## Milestone 2: Scheduling engine and benchmark

### 2.1 The problem in one paragraph
Given docks (with capabilities, opening hours and outages), trucks (arrival time, loading time,
needed capabilities, priority, deadline) and a number of loading crews, choose a dock and a
start time for every truck so that no rule is broken, and trucks wait as little as possible.
This is a form of the classic *parallel machine scheduling* problem with release times,
deadlines, eligibility constraints and a shared resource. It is NP-hard in general, which is why
practical systems use heuristics.

### 2.2 Modelling with records
`Interval`, `Dock`, `Job`, `Assignment` and `Schedule` are Java **records**: immutable data with
`equals`/`hashCode`/`toString` generated. Compact constructors (`public Job { ... }`) validate
input and make defensive copies (`Set.copyOf`, `List.copyOf`). Immutability matters because the
same problem is handed to several schedulers; none of them can change it.

**Half-open intervals** `[start, end)`: a truck finishing at 10:00 and another starting at 10:00
don't overlap. Using closed intervals here is a classic off-by-one bug.

### 2.3 The algorithm: greedy list scheduling
`ListScheduler.schedule`:
1. Sort the jobs with a comparator.
2. For each job, ask the `Timeline` for the earliest feasible start on each compatible dock.
3. Pick the dock with the lowest score, book it, and move on. If nothing fits, record *why*.

`FcfsScheduler` and `PriorityAwareScheduler` only override the comparator and the score.
This is the **Template Method** pattern: the loop is fixed, and the decisions are pluggable.

### 2.3b The production algorithm: event-driven dispatch (`DispatchScheduler`)
A discrete-event simulation of a dispatcher's day:
1. A clock jumps from event to event: a truck arrives, a booking ends, an outage ends.
2. At each event, arrived trucks sit in a `TreeSet` ordered by urgency (a priority queue that also
   supports removal).
3. While some waiting truck can start *now*, start the most urgent one on the free dock with the
   fewest unneeded special capabilities.
4. Trucks whose deadline passes, or that can no longer finish before closing, are reported.

**Why it beat the first design:** `PriorityAwareScheduler` booked urgent trucks across the
whole day first, which fragmented the timeline. Standard trucks then waited behind slots
reserved for trucks hours away. The dispatcher only decides among trucks that are actually
waiting, so it never leaves a dock idle for a future truck. This is the same reason operating
systems schedule among *ready* processes. Measuring, finding a flaw, and fixing it is a strong
interview story.

### 2.4 Finding the earliest feasible start (`Timeline`)
A slot only becomes free when something ends: a booking on the dock, an outage, or another
dock's booking that was holding a crew. So the candidate start times are "the job's earliest
time" plus "every end time after it". The scheduler checks them in order (a `TreeSet` keeps them
sorted and unique) and takes the first that passes every rule. That gives the exact earliest
start without scanning minute by minute.

**Crew capacity** uses a **sweep line**: turn each interval into a +1 event at its start and a
−1 event at its end, sort them, and keep a running total. The maximum running total is the peak
number of crews in use. Ends sort before starts at the same instant, because of half-open intervals.

Complexity: about O(n²) per job and dock in the worst case. Interviewers like hearing that you
measured it: the benchmark reports the compute time.

### 2.5 Determinism
The same input must always give the same schedule, otherwise results can't be audited or
reproduced. How it's achieved:
- Every comparator ends with the job ID (a *total order*: no ties left to chance).
- Docks are sorted by ID before use, and a strict `<` keeps the first dock on equal scores.
- Nothing iterates over a `HashMap` or `HashSet` where order would affect the result.
- `Random` with a fixed seed is specified to produce the same sequence on every JVM.
The test `inputOrderNeverChangesTheResult` shuffles jobs and reverses docks, then expects an
identical schedule.

### 2.6 The independent validator
`ScheduleValidator` re-checks every rule from scratch, sharing no code with the schedulers.
*Why duplicate the logic?* If the scheduler and the checker shared a bug, tests would pass while
schedules were wrong. Independent checking is how safety-critical systems verify outputs. It's
also how DockMind will keep the AI honest in M5: no plan is accepted unless the validator passes it.

### 2.7 Property-style testing
`SchedulerPropertyTest` doesn't check one hand-picked example. It generates 40 warehouse days
(50–370 trucks) and asserts properties that must always hold: zero violations, every job
accounted for, and input order irrelevant. Example-based tests (`SchedulerRulesTest`) document
specific rules; property tests catch the cases you didn't think of.

### 2.8 Benchmarking honestly
- Same seeded workloads for both algorithms; a baseline that isn't sabotaged.
- Unscheduled trucks reported, not averaged away.
- Percentiles (p95), not just averages: one 3-hour wait matters more than ten 1-minute ones.
- Warm-up runs before timing (the JIT compiler speeds code up after it has run a few times).
- The results are generated by the build, so nobody can type in a nicer number.

### 2.9 Interview questions for Milestone 2
13. Why are half-open intervals the right choice for bookings?
14. Walk me through how you find the earliest feasible start. Why are end times sufficient candidates?
15. How do you guarantee determinism? What would break it?
16. What is a sweep-line algorithm? Where did you use it?
17. Why build a separate validator instead of trusting the scheduler's tests?
18. Your scheduler made standard trucks wait longer in some cases. How would you explain that trade-off to a warehouse manager?
19. Why a greedy heuristic and not an optimal solver? When would you switch?
20. Your first design lost to the second. How did you find out, and what did you change?
21. FCFS shows a lower average wait under heavy load. Why is that misleading?

---

## Milestone 3: APIs, security and multi-tenancy

### 3.1 Layers of the shipment service
`ShipmentController` (HTTP in/out) → `ShipmentService` (use cases, transactions) →
`Shipment` + `ShipmentRepository` (rules, data). Controllers hold no business logic, and the
entity holds no HTTP concerns. Separate **DTOs** (`CreateShipmentRequest`, `ShipmentResponse`)
keep the public contract stable when the database changes, and stop internal fields (like the
tenant) from leaking out.

### 3.2 REST design choices
- `POST /api/v1/shipments` returns **201 Created** with a `Location` header.
- `GET /{id}` returns 200 or 404; the list endpoint requires a bounded `from`/`to` (max 7 days),
  so a response can't grow without limit.
- State changes are **commands** (`/arrival`, `/cancel`), not `PATCH status`, so only legal
  transitions exist.
- Status codes: 400 for bad input, 401 for no or invalid token, 403 for wrong role, 404 for
  missing or another tenant's resource, 409 for a conflict (duplicate, illegal transition,
  concurrent edit).
- `/api/v1` versioning: breaking changes go to `/v2` while `/v1` keeps working.

### 3.3 Bean Validation
`@NotBlank`, `@Size`, `@Pattern`, `@Min`/`@Max` and `@NotNull` on the request record, plus a
cross-field rule as an `@AssertTrue` method. `@Valid` on the controller parameter triggers it,
and every failure is reported in one 400, not one at a time.

### 3.4 How a request is authenticated (OAuth2 resource server)
1. The client sends `Authorization: Bearer <JWT>`.
2. Spring Security verifies the **signature** with the identity provider's public key, then
   checks **expiry** and **issuer**.
3. `DockMindJwtConverter` turns the `roles` claims into authorities (`ROLE_DISPATCHER`, ...),
   ignoring unknown roles.
4. URL rules in `SecurityConfiguration` allow or deny the request (403 if denied).
5. The controller reads the tenant from the token with `TenantId.of(jwt)`.

The service never sees a password and keeps no session, because the token is the proof.
**Stateless** means any instance can handle any request, which is what lets M4 run several
copies behind a load balancer.

### 3.5 JWT in 60 seconds
Three base64url parts: header (algorithm), payload (claims: `iss`, `sub`, `exp`,
`tenant_id`, `roles`) and signature. Anyone can *read* a JWT; only the identity provider can
*sign* one. RS256 uses a private key to sign and a public key to verify, so services only ever
hold the public key.

### 3.6 Multi-tenancy, end to end
- The tenant comes **only** from the signed token.
- Every repository call is tenant-scoped.
- Another tenant's shipment returns **404**, so attackers can't probe which IDs exist.
- A `tenantId` smuggled into the JSON body is ignored. A test proves it.

### 3.7 Why CSRF is disabled here
CSRF tricks a browser into sending a request with its cookies automatically. This API uses no
cookies; the bearer token must be added explicitly by code, which a malicious site can't do.
Disabling CSRF is correct for a stateless token API. It would be wrong for a cookie-session app.

### 3.8 Testing security for real
`TestTokens` signs real RS256 JWTs. The tests prove:
- expired, forged-signature, wrong-issuer and missing tokens all return 401, without saying why;
- a viewer can read but not write, and unknown roles grant nothing;
- tenant B can't read, cancel or update tenant A's shipments.

Mocking authentication (for example `@WithMockUser`) would skip signature checks entirely, so
these tests would pass even if verification were broken.

### 3.9 Interview questions for Milestone 3
22. Walk through what happens between receiving a bearer token and running the controller method.
23. Why return 404 rather than 403 for another tenant's shipment?
24. Why is CSRF protection unnecessary for this API, and when would it be required?
25. What's the difference between authentication and authorization? Where is each in this code?
26. Why separate DTOs from entities?
27. Why command endpoints instead of `PATCH status`?
28. How do you test JWT validation without a real identity provider?

---

## Coming in later milestones

Database modeling and transactions (2) · scheduling algorithms and data structures (3) ·
benchmarking (4) · REST design and OpenAPI (5) · OAuth2/JWT and multi-tenancy (6) · microservices,
the gateway and resilience (7) · the Kafka event lifecycle, outbox and idempotency (8) · concurrency
and recovery (9) · Spring AI tools (10) · Angular (11) · observability (12) · Docker, CI/CD and AWS (13).
