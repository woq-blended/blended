#/bin/sh

mkdir -p "../3rdparty"

DIR=scalajs-react-components
BRANCH=master
VERSION=0.7.0

(
  cd ../3rdparty &&
  rm -rf "${DIR}" &&
  git clone https://github.com/woq-blended/scalajs-react-components.git "${DIR}" &&
  cd "${DIR}" &&
  git checkout "${BRANCH}" &&
  sbt -batch clean "+ publishLocal"
)
