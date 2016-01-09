#!/bin/bash
set -e

MVN="$(readlink -f "$(dirname "$0")/mvnw")"

LOG_COUNT=0

function build {
  if [ "$TRAVIS" = "true" ]; then
    LOG_COUNT=$((1+${LOG_COUNT}))
    LOG_FILE=build-${LOG_COUNT}.log
    echo "$MVN $@ > ${LOG_FILE}"
    "$MVN" "$@" > ${LOG_FILE} 2>&1 ; echo "...(last 1000 lines)..." ; tail -n 1000 ${LOG_FILE} ; test ${PIPESTATUS[0]} -eq 0
  else
    "$MVN" "$@"
  fi
}

"$MVN" -version

cd blended-parent

build clean install

cd ..

build clean install -P parent
build clean install -P updater
build clean install -P build
