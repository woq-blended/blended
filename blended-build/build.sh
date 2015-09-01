#!/bin/bash
set -e

MVN="$(readlink -f "$(dirname "$0")/mvnw")"

"$MVN" -version

cd blended-parent
"$MVN" clean install
cd ..
"$MVN" clean install -P parent 2>&1 | grep -v "^([INFO].*|)Download" | grep -v "longer than 100" ; test ${PIPESTATUS[0]} -eq 0
"$MVN" clean install -P updater 2>&1 | grep -v "^([INFO].*|)Download" | grep -v "longer than 100" ; test ${PIPESTATUS[0]} -eq 0
"$MVN" clean install -P build,assembly 2>&1 | grep -v "^([INFO].*|)Download" | grep -v "longer than 100" ; test ${PIPESTATUS[0]} -eq 0
