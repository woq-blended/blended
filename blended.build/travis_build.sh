#!/bin/bash

set -ev

SCRIPT_DIR=$(dirname $0)
BUILD_DIR=$SCRIPT_DIR/..
cd $BUILD_DIR
BUILD_DIR=$(pwd)

cd $BUILD_DIR
mvn clean install -P build 

