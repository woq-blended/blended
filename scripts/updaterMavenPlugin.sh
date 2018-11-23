#!/bin/bash

set -e

git clone https://github.com/woq-blended/blended-updater-maven-plugin.git $TRAVIS_BUILD_DIR/updaterPlugin
cd $TRAVIS_BUILD_DIR/updaterPlugin
git checkout master
mvn clean install

cd $TRAVIS_BUILD_DIR
