#!/usr/bin/env bash
#
# Build every deployable container image for linux/arm64 (AWS Graviton), tagged with a version.
#
#   ./scripts/build-images.sh                    tag = current Git commit; images stay on this machine
#   ./scripts/build-images.sh <tag> <registry>   also tag and push to <registry>/dockmind/<name>:<tag>
#
# Pushing needs a registry login first (for ECR: aws ecr get-login-password | docker login ...).
# Without a registry argument nothing leaves this machine.
set -euo pipefail
cd "$(dirname "$0")/.."

TAG="${1:-$(git rev-parse --short=12 HEAD)}"
REGISTRY="${2:-}"
SERVICES=(api-gateway shipment-service dock-scheduling-service disruption-recovery-service ai-operations-service)

echo "==> Building the services (tests are run by ship.sh / the pipeline)"
./mvnw -B -q -DskipTests package

build() {
  local name="$1" context="$2"
  local image="dockmind/${name}:${TAG}"
  echo "==> ${image}"
  docker buildx build --platform linux/arm64 --load -t "${image}" "${context}"
  if [[ -n "${REGISTRY}" ]]; then
    docker tag "${image}" "${REGISTRY}/${image}"
    docker push "${REGISTRY}/${image}"
  fi
}

for s in "${SERVICES[@]}"; do build "$s" "$s"; done
build keycloak keycloak
build frontend frontend

echo "Built tag ${TAG}${REGISTRY:+ and pushed to ${REGISTRY}}."
