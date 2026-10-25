# DOCKMIND AI — MASTER DEVELOPMENT PROMPT

Act as my principal Java backend engineer, software architect, DevOps engineer, QA engineer, and technical mentor.

I am a beginner-to-intermediate Java developer, graduating with an MS in Computer Science in December 2026. I want this project to demonstrate production-oriented Java backend engineering for US entry-level Software Engineer, Java Developer, and Backend Engineer positions.

You will implement the project using Claude's coding capabilities in my local development environment. Do not merely describe what to build. When you have filesystem, terminal, and GitHub access, actually implement, run, test, and commit the code.

## PRODUCT

Name: DockMind AI

Description: A multi-tenant warehouse dock scheduling and disruption recovery platform.

Build a working platform where logistics operators can create shipments, register trucks, schedule loading docks, manage appointments, detect delays, handle dock failures, and automatically compute feasible alternative schedules.

The core differentiator must be auditable, constraint-aware disruption recovery with benchmarked reductions in simulated waiting time compared with a defined baseline.

Use synthetic datasets. Do not claim production customers or real-world business savings.

## REQUIRED STACK

Backend:
- Java 21
- Spring Boot, using a mutually compatible stable version
- Spring Framework / Spring MVC
- Spring Cloud Gateway
- Spring Security
- Spring Data JPA and Hibernate
- PostgreSQL
- Apache Kafka
- Redis
- Spring AI with OpenAI
- Maven multi-module build
- REST APIs and OpenAPI/Swagger
- Flyway database migrations
- Bean Validation
- SLF4J and Logback

Frontend:
- Angular
- TypeScript
- Angular Material
- Tailwind CSS
- Angular routing and reactive forms
- Charting library for operational metrics

Testing:
- JUnit 5
- Mockito
- Testcontainers
- Spring Boot integration tests
- REST API contract tests
- Frontend unit tests
- End-to-end workflow tests
- Load tests using k6 or Gatling

DevOps:
- Git and GitHub
- Docker and Docker Compose
- Jenkins CI/CD
- Terraform
- Ansible where deployment configuration genuinely requires it
- Linux deployment environment
- AWS ECS, ECR, RDS, and S3
- Prometheus, Grafana, and OpenTelemetry
- Secrets management and environment-specific configurations

Security:
- Spring Security
- OAuth2/OIDC
- JWT access token validation
- RBAC with ADMIN, DISPATCHER, and VIEWER roles
- Multi-tenant data isolation
- Rate limiting
- Input validation
- Secure error handling
- Audit logging
- No hardcoded credentials
- Authorization checks for all sensitive APIs and AI tools

## ARCHITECTURE

Use genuine microservices with service-owned domain data and no cross-service database joins.

Initial services:
1. API Gateway
2. Identity and Tenant Service, or an appropriately configured external OIDC provider
3. Shipment Service
4. Dock Scheduling Service
5. Disruption Recovery Service
6. AI Operations Service

Each service must have a clearly bounded responsibility.

Use REST for synchronous operations and Kafka only for events that benefit from asynchronous processing.

Implement:
- Transactional outbox pattern for important events
- Idempotent consumers
- Retries with bounded backoff
- Dead-letter handling
- Event versioning
- Correlation IDs
- Health and readiness endpoints
- Timeouts and circuit breakers where appropriate
- Distributed tracing
- Safe concurrent updates
- Optimistic locking where suitable
- Clear failure handling and recovery

Do not add Kubernetes merely to make the stack bigger.

## CORE BUSINESS FUNCTIONALITY

Implement:
- Warehouse and dock registration
- Dock capability constraints
- Shipment booking and truck arrivals
- Appointment scheduling
- Scheduling conflicts and capacity validation
- Late arrival simulation
- Dock outage simulation
- Priority shipment handling
- Scheduling algorithm with deterministic results
- Recovery plan comparison
- Human approval for significant schedule changes
- Operational dashboard
- Audit trail and scheduling history

The scheduling engine should consider dock compatibility, loading time, arrival windows, appointment priority, availability, and resource capacity.

Use appropriate algorithms, data structures, and concurrency controls.

Spring AI may explain recommendations or call authorized read-only operational tools, but it must not invent schedules or bypass deterministic scheduling validation.

## BUSINESS METRICS

Create reproducible simulation and benchmarking tools that measure:
- Number of shipments processed
- Average and p95 waiting time
- Dock utilization
- Scheduling conflicts
- Successful recovery percentage
- Scheduling computation time
- API p50/p95/p99 latency
- Kafka event throughput
- Event duplication handling
- Failure recovery time

Compare the optimized scheduler against a basic first-come-first-served scheduler under equivalent seeded workloads.

Produce CSV or JSON reports and benchmark documentation.

Never invent metrics or describe simulated cost savings as verified real-world savings.

## IMPLEMENTATION PROCESS

First inspect the current environment, verify installed tools, and check whether this repository already exists.

Never overwrite existing work without inspecting it.

Produce:
1. PRODUCT_SPEC.md
2. ARCHITECTURE.md
3. ROADMAP.md
4. DECISIONS.md
5. PROJECT_CONTEXT.md
6. LEARNING_GUIDE.md

Plan development in independently testable phases.

Examples:
- Repository foundation
- Database and persistence
- Identity/security
- Shipment APIs
- Scheduling engine
- Kafka workflows
- Disruption recovery
- Spring AI
- Angular frontend
- Testing and benchmarks
- Observability
- CI/CD and cloud deployment
- Documentation and final verification

Adapt the plan when technical dependencies require it.

For every phase:
1. Explain the goal briefly.
2. Implement real working functionality.
3. Add appropriate tests.
4. Execute available checks.
5. Fix failures.
6. Update documentation.
7. Commit using the configured Git identity.
8. Push to the configured GitHub repository when authentication and connectivity are available.
9. Confirm the actual commit hash and push result.
10. Update PROJECT_CONTEXT.md with the current state and exact next steps.

If authentication, deployment credentials, cost approval, or other external input is required, stop that operation and explain exactly what I must do. Continue with independent tasks where possible.

Never claim a push or deployment succeeded without verifying it.

## GITHUB RULES

Use my own GitHub account, Git credentials, and configured author identity.

Do not add fabricated co-authors or other contributor identities. Do not manipulate Git history to conceal genuine external contributions.

AI assistance is allowed, but preserve accurate authorship and attribution. Do not copy entire third-party repositories or violate licenses.

All code must be committed incrementally.

Do not place secrets, API keys, access tokens, private datasets, or passwords in Git.

## LEARNING AND HANDOFF

I will use ChatGPT to study the finished project deeply.

Maintain LEARNING_GUIDE.md with:
- Beginner explanation of every major module
- Important Java concepts
- Spring annotations and dependency injection
- Data structures and algorithms
- REST API design
- Database modeling and transactions
- Kafka event lifecycle
- Authentication and authorization
- Spring AI functionality
- Concurrency and failure recovery
- Docker and CI/CD
- AWS deployment
- Testing strategies
- Architecture tradeoffs
- Common interview questions

Maintain PROJECT_CONTEXT.md with:
- Current architecture
- Implemented and incomplete features
- File/module locations
- Dependencies and environment commands
- Current test results
- Verified metrics
- Git commit hash
- Known issues
- Next phase

When the conversation gets long, create a concise SESSION_HANDOFF.md so the next Claude conversation can continue from the repository.

## QUALITY REQUIREMENTS

No fake implementations, placeholder features presented as complete, hardcoded success responses, invented benchmarks, or unnecessary complexity.

Prioritize correctness, security, reliability, tests, maintainability, and demonstrated business value.

Do not declare completion until the essential application flows, tests, documentation, frontend, observability, and deployment configuration are verified.

START NOW:
Inspect the environment and existing files, present the architecture and phased roadmap, and implement the first verified phase. Do not attempt the entire system in one unreviewable change.