#!/usr/bin/env bash

set -eo pipefail

CODING_REGISTRY_URL=${CODING_REGISTRY_URL:-codingcorp-docker.pkg.coding.net}
CODING_REGISTRY_REPO=${CODING_REGISTRY_REPO:-registry/build}
CODING_REGISTRY_USER="${CODING_REGISTRY_USER}"
CODING_REGISTRY_PASS="${CODING_REGISTRY_PASS}"
ARTIFACT=orca

CD_REGISTRY_URL=${CD_REGISTRY_URL:-selinaxeon-docker.pkg.coding.net}
CD_REGISTRY_REPO=${CD_REGISTRY_REPO:-testing/build}
CD_REGISTRY_USER="${CD_REGISTRY_USER}"
CD_REGISTRY_PASS="${CD_REGISTRY_PASS}"

COMMIT_ID=$(git rev-parse HEAD)

if ! grep -q ${CODING_REGISTRY_URL} ~/.docker/config.json; then
    echo "logging into ${CODING_REGISTRY_URL}..."
    echo ${CODING_REGISTRY_PASS} | docker login -u ${CODING_REGISTRY_USER} --password-stdin ${CODING_REGISTRY_URL} || echo "[ERROR] Login $CODING_REGISTRY_URL failed"
fi

docker build -t ${ARTIFACT}-compile -f Dockerfile.compile .
docker build -t ${ARTIFACT}:latest -f Dockerfile.slim .

if [ "$1" = "--push" ]; then
  docker tag ${ARTIFACT}:latest ${CODING_REGISTRY_URL}/${CODING_REGISTRY_REPO}/${ARTIFACT}:${COMMIT_ID}
  docker push ${CODING_REGISTRY_URL}/${CODING_REGISTRY_REPO}/${ARTIFACT}:${COMMIT_ID}

  if [ "$GIT_BRANCH" = "origin/release" ]; then
    if ! grep -q ${CD_REGISTRY_URL} ~/.docker/config.json; then
        echo "logging into ${CD_REGISTRY_URL}..."
        echo ${CD_REGISTRY_PASS} | docker login -u ${CD_REGISTRY_USER} --password-stdin ${CD_REGISTRY_URL} || echo "[ERROR] Login $CD_REGISTRY_URL failed"
    fi
    docker tag ${ARTIFACT}:latest ${CD_REGISTRY_URL}/${CD_REGISTRY_REPO}/${ARTIFACT}:${COMMIT_ID}
    docker push ${CD_REGISTRY_URL}/${CD_REGISTRY_REPO}/${ARTIFACT}:${COMMIT_ID}
  fi
fi
