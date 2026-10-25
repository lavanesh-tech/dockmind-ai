# Session Handoff

For the next Claude conversation: read this, then `docs/PROJECT_CONTEXT.md`, then `docs/ROADMAP.md`.

- **Project:** DockMind AI, a multi-tenant dock scheduling and disruption recovery platform.
  Full brief in `docs/MASTER_PROMPT.md`.
- **Repo:** `~/Desktop/dockmind-ai` on the developer's Mac; GitHub `lavanesh-tech/dockmind-ai`.
- **Author rule:** only `Lavanesh Thirukonda Mahendran <lavanesh532@gmail.com>`. No co-author
  trailers, ever. `scripts/ship.sh` enforces it.
- **Workflow:** Claude writes code into the folder → the developer runs
  `./scripts/ship.sh "Mx: ..."` → Claude reads the output and fixes failures → repeat until `SHIPPED`.
- **Environment limits:** Claude's cloud workspace and the device sandbox cannot reach Maven
  Central, so Claude cannot compile; the developer's Mac can. Docker Desktop must be running for tests.
- **Stack versions:** Java 21, Spring Boot 4.1.1, Spring Cloud 2025.1.3, PostgreSQL 17,
  Testcontainers 2.x (package `org.testcontainers.postgresql`).
- **Where we are:** M1 code complete; Phase 1 portion verified (21 tests); persistence awaiting
  the first `ship.sh`. Next: M2 scheduler and benchmark.
