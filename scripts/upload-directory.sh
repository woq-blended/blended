#!/bin/sh

set UPLOAD_DIR=$1
set UPLOAD_FILE=$2

cd $UPLOAD_DIR
tar -czf ../$UPLOAD_FILE.tgz .

$TRAVIS_BUILD_DIR/scripts/dropbox_uploader.sh upload $UPLOAD_FILE.tgz travis/$1/$UPLOAD_FILE.tgz
