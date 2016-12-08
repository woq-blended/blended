#!/usr/bin/env bash

set -x

val SCRIPT_DIR=`dirname "$0"`

sh $SCRIPT_DIR/docker_clean.sh

MVN="$(readlink -f "$(dirname "$0")/mvnw")"

$MVN install -P docker -Ddocker.host=$DOCKER_HOST -Ddocker.port=$DOCKER_PORT





