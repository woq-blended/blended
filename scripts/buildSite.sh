#!/bin/bash

set -x
set -e

mkdir -p doc/_site

sbt -mem 4096 cleanCoverage unidoc

mv target/scala-2.12/scoverage-report doc/_site/coverage
mv target/scala-2.12/unidoc doc/_site/scaladoc

cd doc
jekyll build
