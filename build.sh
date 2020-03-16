#!/usr/bin/env bash

set -eo pipefail

CODING_REGISTRY_URL=${CODING_REGISTRY_URL:-codingcorp-docker.pkg.coding.net}
CODING_REGISTRY_REPO=${CODING_REGISTRY_REPO:-registry/build}
CODING_REGISTRY_USER="${CODING_REGISTRY_USER}"
CODING_REGISTRY_PASS="${CODING_REGISTRY_PASS}"
ARTIFACT=orca

COMMIT_ID=$(git rev-parse HEAD)

docker build -t compile -f Dockerfile.compile .
docker build -t ${ARTIFACT}:latest -f Dockerfile.slim .

if [ "$1" = "--push" ]; then
  if ! grep -q ${CODING_REGISTRY_URL} ~/.docker/config.json; then
    echo "logging into ${CODING_REGISTRY_URL}..."
    echo ${CODING_REGISTRY_PASS} | docker login -u ${CODING_REGISTRY_USER} --password-stdin ${CODING_REGISTRY_URL} || echo "[ERROR] Login $CODING_REGISTRY_URL failed"
  fi
  docker tag ${ARTIFACT}:latest ${CODING_REGISTRY_URL}/${CODING_REGISTRY_REPO}/${ARTIFACT}:${COMMIT_ID}
  docker push ${CODING_REGISTRY_URL}/${CODING_REGISTRY_REPO}/${ARTIFACT}:${COMMIT_ID}
fi
