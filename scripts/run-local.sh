#!/usr/bin/env bash
#
# Run all of DockMind on this Mac and open it in the browser.
#
#   ./scripts/run-local.sh          build, start everything, then serve the web app on http://localhost:4200
#   ./scripts/run-local.sh e2e      run the end-to-end browser test against the running system
#   ./scripts/run-local.sh stop     stop all containers (data is kept)
#
# Secrets live only in .env (gitignored). Missing values are generated here, once.
set -euo pipefail
cd "$(dirname "$0")/.."

step() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
fail() { printf '\n\033[31mSTOPPED: %s\033[0m\n' "$*" >&2; exit 1; }

ensure_env() {
  touch .env
  for name in DB_PASSWORD KC_ADMIN_PASSWORD DEMO_USER_PASSWORD DOCKMIND_SMOKE_CLIENT_SECRET; do
    if ! grep -Eq "^${name}=.+" .env; then
      sed -i.bak "/^${name}=/d" .env && rm -f .env.bak
      echo "${name}=$(openssl rand -hex 12)" >> .env
      echo "Generated ${name} in .env"
    fi
  done
  grep -Eq '^DB_USERNAME=.+' .env || echo "DB_USERNAME=dockmind" >> .env
}

env_value() { grep -E "^$1=" .env | tail -1 | cut -d= -f2-; }

wait_for() {
  local url="$1" name="$2"
  for _ in $(seq 1 90); do
    if curl -fs -o /dev/null "$url"; then echo "$name is up"; return 0; fi
    sleep 2
  done
  fail "$name did not start. See: docker compose --profile app logs"
}

case "${1:-start}" in
  stop)
    docker compose --profile app down
    ;;

  e2e)
    step "End-to-end test (the system must be running: ./scripts/run-local.sh in another window)"
    curl -fs -o /dev/null http://localhost:4200 || fail "The web app is not running on http://localhost:4200."
    cd frontend
    [[ -d node_modules ]] || npm ci --no-audit --no-fund
    npx playwright install chromium
    DEMO_USER_PASSWORD="$(cd .. && env_value DEMO_USER_PASSWORD)" npm run e2e
    ;;

  start)
    step "1/5 Checking tools"
    docker info >/dev/null 2>&1 || fail "Docker is not running. Open Docker Desktop and try again."
    command -v node >/dev/null || fail "Node.js is not installed (https://nodejs.org)."
    ensure_env
    chmod 644 keycloak/realm-dockmind.json   # Keycloak runs as a non-root user and must be able to read it

    step "2/5 Building the services (tests skipped here; ship.sh runs them)"
    ./mvnw -B -q -DskipTests package

    step "3/5 Starting databases, Kafka, Redis, Keycloak and the services"
    docker compose --profile app up -d --build

    step "4/5 Waiting for everything to be ready"
    wait_for http://localhost:8180/realms/dockmind "Keycloak"
    wait_for http://localhost:8080/actuator/health "API gateway"
    for port in 8081 8082 8083 8084; do wait_for "http://localhost:${port}/actuator/health" "Service on :${port}"; done

    step "5/5 Starting the web app"
    cd frontend
    [[ -d node_modules ]] || npm ci --no-audit --no-fund
    cat <<INFO

  Open http://localhost:4200 in Chrome and sign in as one of:
    admin.acme · dispatcher.acme · viewer.acme · dispatcher.globex
  Password: $(cd .. && env_value DEMO_USER_PASSWORD)   (from your .env)

  Stop the web app with Ctrl+C. Stop everything else with: ./scripts/run-local.sh stop
INFO
    npm start
    ;;

  *)
    echo "Usage: ./scripts/run-local.sh [start|e2e|stop]" >&2
    exit 2
    ;;
esac
