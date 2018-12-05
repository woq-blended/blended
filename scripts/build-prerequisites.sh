#!/bin/bash -e

publishLocal() {
  cd "${TRAVIS_BUILD_DIR}"
  local REPO="https://github.com/woq-blended/$0.git"
  echo "Cloning ${REPO}..."
  git clone --branch master --depth 1 \
    "$REPO" "${TRAVIS_BUILD_DIR}/$0"
  cd "${TRAVIS_BUILD_DIR}/$0"
  echo "Building and (local) publishing ${REPO}..."
  sbt publishLocal
  cd "${TRAVIS_BUILD_DIR}"
}

publishLocal "sbt-testlogconfig"
publishLocal "sbt-jbake"
