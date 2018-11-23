#!/bin/bash

git clone https://github.com/woq-blended/blended.mgmt.ui.git mgmtUi
cd mgmtUi
git checkout master
sbt test publishM2
cd $TRAVIS_BUILD_DIR