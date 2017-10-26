#!/usr/bin/env bash

set -e

SCRIPT_DIR=$(dirname $0)
source $SCRIPT_DIR/00_common.sh

BUILD_DIR=$1

MVN_REPO=$(mvnRepo $BUILD_DIR)
IVY_REPO=$(ivyRepo $BUILD_DIR)

execMaven $MVN_REPO $IVY_REPO docker install
exec docker_clean.sh
