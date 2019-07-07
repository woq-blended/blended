#!/bin/bash

git clone https://github.com/woq-blended/blended.itestsupport.git $TRAVIS_BUILD_DIR/blendedITest
cd $TRAVIS_BUILD_DIR/blendedITest
git checkout master
sbt update publishLocal
cd $TRAVIS_BUILD_DIR
