#!/bin/sh

set DOWNLOAD_DIR=$1
set DOWNLOAD_FILE=$2

mkdir -p $DOWNLOAD_DIR
cd $DOWNLOAD_DIR
$TRAVIS_BUILD_DIR/scripts/dropbox_uploader.sh download travis/$1/$DOWNLOAD_FILE.tgz $DOWNLOAD_FILE.tgz
tar -xzf $DOWNLOAD_FILE.tgz
rm $DOWNLOAD_FILE.tgz

cd $TRAVIS_BUILD_DIR
