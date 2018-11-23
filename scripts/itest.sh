#!/bin/bash

set -e

set BLENDED_VERSION=$(cat $TRAVIS_BUILD_DIR/version.txt)

git clone https://github.com/woq-blended/blended.container.git $TRAVIS_BUILD_DIR/container
cd container
git checkout master
docker --version
mvn clean install -P docker,itest

docker push atooni/blended_mgmt:$BLENDED_VERSION
docker push atooni/blended_node:$BLENDED_VERSION

cd $TRAVIS_BUILD_DIR