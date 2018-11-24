#!/bin/bash

set -e

set BLENDED_VERSION=$(cat $TRAVIS_BUILD_DIR/version.txt)

git clone https://github.com/woq-blended/blended.container.git $TRAVIS_BUILD_DIR/container
cd $TRAVIS_BUILD_DIR/container
git checkout master
docker --version
ps -ef | grep docker

#Avoid non zero return code
rm -f $HOME/.docker/config.json || true

mkdir -p $TRAVIS_BUILD_DIR/container/.mvn
echo "-Ddocker.host=$DOCKER_HOST -Ddocker.port=$DOCKER_PORT" > $TRAVIS_BUILD_DIR/container/.mvn/maven.config

mvn clean install -P docker,itest

echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
docker push atooni/blended_mgmt:$BLENDED_VERSION
docker push atooni/blended_node:$BLENDED_VERSION

cd $TRAVIS_BUILD_DIR