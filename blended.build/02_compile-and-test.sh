#!/bin/bash

MVN="$(readlink -f "$(dirname "$0")/mvnw")"

LOG_COUNT=0

if [ "$TRAVIS" = "true" ]; then
  echo "Detected Travis CI environment"
fi

function build {
  if [ "$TRAVIS" = "true" ]; then
    LOG_COUNT=$((1+${LOG_COUNT}))
    LOG_FILE=build-${LOG_COUNT}.log
    echo "$MVN $@ > ${LOG_FILE}"
    "$MVN" "$@" > ${LOG_FILE} 2>&1 ; MVN_STATUS=$? ; echo "...(last 1000 lines)..." ; tail -n 1000 ${LOG_FILE} ; test "$MVN_STATUS" -eq 0
  else
    "$MVN" "$@"
  fi
}

"$MVN" -version

$MVNW clean install -P build "$@"
