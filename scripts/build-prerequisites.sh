#!/bin/bash -e

publishLocal() {
  cd "${TRAVIS_BUILD_DIR}"
  local REPO="https://github.com/woq-blended/$1.git"
  local REPODIR="${TRAVIS_BUILD_DIR}/$1"
  rm -Rf ${REPODIR}
  echo "Cloning ${REPO}..."
  git clone --branch master --depth 1 \
    "$REPO" "$REPODIR"
  cd "$REPODIR"
  echo "Building and (local) publishing ${REPO}..."
  sbt publishLocal
  cd "${TRAVIS_BUILD_DIR}"
}

publishLocal "sbt-testlogconfig"
publishLocal "sbt-jbake"
publishLocal "sbt-filterresources"
