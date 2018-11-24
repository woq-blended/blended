#!/bin/sh

rm -Rf $TRAVIS_BUILD_DIR/target/results
mkdir -p $TRAVIS_BUILD_DIR/target/results
find $TRAVIS_BUILD_DIR -name "TEST*.xml" -printf "cp %p $TRAVIS_BUILD_DIR/target/results/%f\n" | sh

$TRAVIS_BUILD_DIR/srcipts/upload-directory.sh $TRAVIS_BUILD_DIR/target/results results
