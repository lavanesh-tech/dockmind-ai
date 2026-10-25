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

## Coming in later milestones

Database modeling and transactions (2) · scheduling algorithms and data structures (3) ·
benchmarking (4) · REST design and OpenAPI (5) · OAuth2/JWT and multi-tenancy (6) · microservices,
the gateway and resilience (7) · the Kafka event lifecycle, outbox and idempotency (8) · concurrency
and recovery (9) · Spring AI tools (10) · Angular (11) · observability (12) · Docker, CI/CD and AWS (13).
