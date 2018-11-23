#!/bin/sh

rm -Rf $TRAVIS_BUILD_DIR/target/results
mkdir -p $TRAVIS_BUILD_DIR/target/results
find $TRAVIS_BUILD_DIR -name "TEST*.xml" -printf "cp %p $TRAVIS_BUILD_DIR/target/results/%f\n" | sh
cd $TRAVIS_BUILD_DIR
tar -czf target.tgz target

$TRAVIS_BUILD_DIR/scripts/dropbox_uploader.sh upload target.tgz travis/$1/target.tgz
