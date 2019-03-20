#!/bin/bash

git clone https://github.com/woq-blended/blended.mgmt.ui.git $TRAVIS_BUILD_DIR/mgmtUi
cd $TRAVIS_BUILD_DIR/mgmtUi
git checkout master
sbt update test publishLocal publishM2
cd $TRAVIS_BUILD_DIR
