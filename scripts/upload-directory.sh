#!/bin/sh

set -x

UPLOAD_DIR=$1
UPLOAD_FILE=$2

LAST_DIR=$(pwd)
cd $UPLOAD_DIR
tar -czf ../$UPLOAD_FILE.tgz .

$TRAVIS_BUILD_DIR/scripts/dropbox_uploader.sh upload ../$UPLOAD_FILE.tgz travis/$TRAVIS_BUILD_NUMBER/$UPLOAD_FILE.tgz

cd $LAST_DIR
