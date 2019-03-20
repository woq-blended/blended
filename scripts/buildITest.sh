#!/bin/bash

git clone https://github.com/woq-blended/blended.itestsupport.git $TRAVIS_BUILD_DIR/blendedITest
cd $TRAVIS_BUILD_DIR/blendedITest
git checkout master
sbt update publishM2
cd $TRAVIS_BUILD_DIR
