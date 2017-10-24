#!/usr/bin/env bash

SCRIPT_DIR=`dirname "$0"`
MVN="$(readlink -f "$(dirname "$0")/mvnw")"

sh $SCRIPT_DIR/docker_clean.sh

$MVN install -P itest -Ddocker.host=$DOCKER_HOST -Ddocker.port=$DOCKER_PORT

sh $SCRIPT_DIR/docker_clean.sh
