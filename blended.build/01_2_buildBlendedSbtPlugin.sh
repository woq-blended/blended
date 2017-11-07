#!/usr/bin/env bash

set -e

SCRIPT_DIR=$(dirname $0)
source $SCRIPT_DIR/00_common.sh

BUILD_DIR=$1

MVN_REPO=$(mvnRepo $BUILD_DIR)
IVY_REPO=$(ivyRepo $BUILD_DIR)

THIRD_PTY_DIR=$BUILD_DIR/3rdparty

mkdir -p $THIRD_PTY_DIR

DIR=sbt-blended
BRANCH=master
VERSION=0.1-SNAPSHOT

(
  cd $THIRD_PTY_DIR &&
  rm -rf "${DIR}" &&
  git clone https://github.com/woq-blended/blended-sbt.git "${DIR}" &&
  cd "${DIR}" &&
  git checkout "${BRANCH}" &&
  sbt -ivy $IVY_REPO -Dmaven.repo.local=$MVN_REPO -batch clean "+ publishLocal"
  mvn -Dmaven.repo.local=$MVN_REPO install:install-file -DartifactId=scalajs-react-components_sjs0.6_2.11 -DgroupId=com.olvind -Dversion=${VERSION} -DgeneratePom=true -Dfile=core/target/scala-2.11/scalajs-react-components_sjs0.6_2.11-${VERSION}.jar -Dpackaging=jar
  mvn -Dmaven.repo.local=$MVN_REPO install:install-file -DartifactId=scalajs-react-components_sjs0.6_2.12 -DgroupId=com.olvind -Dversion=${VERSION} -DgeneratePom=true -Dfile=core/target/scala-2.12/scalajs-react-components_sjs0.6_2.12-${VERSION}.jar -Dpackaging=jar
)
