#!/bin/bash

set -e

set BLENDED_VERSION=$(cat $TRAVIS_BUILD_DIR/version.txt)

git clone https://github.com/woq-blended/blended.container.git $TRAVIS_BUILD_DIR/container
cd $TRAVIS_BUILD_DIR/container
git checkout master
docker --version
ps -ef | grep docker

mkdir -p $TRAVIS_BUILD_DIR/container/.mvn
echo "-Ddocker.host=$DOCKER_HOST -Ddocker.port=$DOCKER_PORT" > $TRAVIS_BUILD_DIR/container/.mvn/maven.config

mvn clean install -P docker,itest

docker push atooni/blended_mgmt:$BLENDED_VERSION
docker push atooni/blended_node:$BLENDED_VERSION

cd $TRAVIS_BUILD_DIR