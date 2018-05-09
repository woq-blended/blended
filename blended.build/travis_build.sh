#!/bin/bash

set -x
set -e 

SCRIPT_DIR=$(dirname $0)
BUILD_DIR=$SCRIPT_DIR/..
cd $BUILD_DIR
BUILD_DIR=$(pwd)

THIRD_PARTY_DIR=$BUILD_DIR/target/3rdparty

rm -Rf $THIRD_PARTY_DIR
mkdir -p $THIRD_PARTY_DIR

cd $THIRD_PARTY_DIR
THIRD_PARTY_DIR=$(pwd)

git clone https://github.com/woq-blended/react4s.git
git clone https://github.com/woq-blended/router4s.git

cd $THIRD_PARTY_DIR/react4s
sbt publishLocal

cd $THIRD_PARTY_DIR/router4s
sbt publishLocal

cd $BUILD_DIR
mvn clean install -P build 

