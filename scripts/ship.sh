#!/usr/bin/env bash
#
# Verify, commit and push one milestone in a single command.
#
#   ./scripts/ship.sh "M1: foundation and persistence"
#
# It stops at the first problem and changes nothing on GitHub unless every check passes:
#   1. tools present (JDK 21+, Docker running, git identity correct)
#   2. code formatted (Spotless)
#   3. full build + unit + integration tests (./mvnw verify)
#   4. no secrets in the changes about to be committed
#   5. commit authored only by the expected identity, no co-author trailers
#   6. push, then confirm GitHub's main points at the same commit
set -euo pipefail

cd "$(dirname "$0")/.."

MSG="${1:-}"
if [[ -z "$MSG" ]]; then
  echo 'Usage: ./scripts/ship.sh "M1: short description"' >&2
  exit 2
fi

EXPECTED_NAME="Lavanesh Thirukonda Mahendran"
EXPECTED_EMAIL="lavanesh532@gmail.com"
BRANCH="main"
LOG="$(mktemp -t dockmind-ship.XXXXXX)"

step() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
fail() { printf '\n\033[31mSTOPPED: %s\033[0m\n' "$*" >&2; exit 1; }

step "1/6 Checking tools"
java -version 2>&1 | head -1
docker info >/dev/null 2>&1 || fail "Docker is not running. Open Docker Desktop, wait until it says 'running', then run this again."
[[ "$(git config user.name)" == "$EXPECTED_NAME" ]]   || fail "git user.name is '$(git config user.name)', expected '$EXPECTED_NAME'."
[[ "$(git config user.email)" == "$EXPECTED_EMAIL" ]] || fail "git user.email is '$(git config user.email)', expected '$EXPECTED_EMAIL'."
echo "git identity: $EXPECTED_NAME <$EXPECTED_EMAIL>"

step "2/6 Formatting code"
./mvnw -q -B spotless:apply

step "3/6 Building and running all tests (log: $LOG)"
if ! ./mvnw -B verify 2>&1 | tee "$LOG"; then
  echo
  grep -E "Tests run:|FAIL|ERROR\]" "$LOG" | tail -40 || true
  fail "Build or tests failed. Copy everything above this line and paste it to Claude."
fi
echo
echo "Test summary:"
grep -E "^\[INFO\] Tests run: [0-9]+, Failures" "$LOG" | grep -v " -- in " || true

step "4/6 Scanning changes for secrets"
git add -A
SECRET_PATTERNS='(sk-[A-Za-z0-9_-]{20,}|AKIA[0-9A-Z]{16}|-----BEGIN [A-Z ]*PRIVATE KEY-----|ghp_[A-Za-z0-9]{30,}|xox[baprs]-[A-Za-z0-9-]{10,})'
if git diff --cached -U0 | grep -E '^\+' | grep -Eq "$SECRET_PATTERNS"; then
  git diff --cached -U0 | grep -E '^\+' | grep -En "$SECRET_PATTERNS" | sed -E 's/(.{12}).*/\1…(hidden)/'
  git reset -q
  fail "Something that looks like a secret is about to be committed. Nothing was committed."
fi
if git diff --cached --name-only | grep -E '(^|/)\.env($|\.)' | grep -vq '\.env\.example$'; then
  git reset -q
  fail "A .env file is staged. Nothing was committed."
fi
echo "No secrets found."

step "5/6 Committing"
if git diff --cached --quiet; then
  echo "No new changes to commit; checking that GitHub is up to date."
else
  git commit -q -m "$MSG"
fi
AUTHOR="$(git log -1 --format='%an <%ae>')"
[[ "$AUTHOR" == "$EXPECTED_NAME <$EXPECTED_EMAIL>" ]] || fail "Last commit author is '$AUTHOR'."
if git log -1 --format='%B' | grep -Eiq '^(co-authored-by|signed-off-by):'; then
  fail "Last commit has a co-author or sign-off trailer. Not pushing."
fi
COMMIT="$(git rev-parse HEAD)"
echo "Commit $COMMIT by $AUTHOR"

step "6/6 Pushing to GitHub"
git push -u origin "$BRANCH"
git fetch -q origin "$BRANCH"
REMOTE="$(git rev-parse "origin/$BRANCH")"
[[ "$REMOTE" == "$COMMIT" ]] || fail "GitHub's $BRANCH is $REMOTE but local is $COMMIT."

printf '\n\033[32mSHIPPED: %s\033[0m\n' "$MSG"
echo "Commit: $COMMIT"
echo "GitHub: $(git remote get-url origin | sed 's/\.git$//')/commit/$COMMIT"
