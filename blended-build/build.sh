#!/bin/bash
set -e

MVN="$(readlink -f "$(dirname "$0")/mvnw")"

cd blended-parent
"$MVN" clean install
cd ..
"$MVN" clean install -P parent | grep -v "^[INFO].*Download" | grep -v "longer than 100" ; test ${PIPESTATUS[0]} -eq 0
"$MVN" clean install -P build,assembly | grep -v "^[INFO].*Download" | grep -v "longer than 100" ; test ${PIPESTATUS[0]} -eq 0
