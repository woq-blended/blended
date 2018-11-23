#!/bin/sh

rm -Rf target/results
mkdir -p target/results
find . -name "TEST*.xml" -printf "cp %p target/results/%f\n" | sh

tar -czf target.tgz target

./dropbox_uploader.sh upload target.tgz travis/$1/target.tgz
