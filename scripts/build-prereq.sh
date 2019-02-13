#!/bin/bash -e

WORKDIR="${TRAVIS_BUILD_DIR}"

if [ "x${WORKDIR}" = "x" ]; then
  WORKDIR="$(pwd)"
fi

publishLocal() {
  if [ "x$1" = "x" ] ; then
    false
    return
  fi

  cd "${WORKDIR}"
  local REPO="https://github.com/woq-blended/$1.git"
  local REPODIR="${WORKDIR}/$1"
  rm -Rf ${REPODIR}
  echo "Cloning ${REPO}..."
  git clone --branch master --depth 1 "$REPO" "$REPODIR"
  cd "$REPODIR"

  local TOUCHDIR="${WORKDIR}/.prereq-refs"
  local TOUCHFILE="${TOUCHDIR}/$1"
  local GITREF="$(git rev-parse HEAD || echo xxx) sbt publishLocal"
  local LAST_GITREF="$(cat ${TOUCHFILE} 2> /dev/null || echo --- )"

  if [ "${GITREF}" = "${LAST_GITREF}" ] ; then

    echo "Skipping build of prerequisite $1 because cached build exists"

  else

    echo "Building and (local) publishing ${REPO}..."
    sbt publishLocal

    mkdir -p "${TOUCHDIR}"
    echo "${GITREF}" > "${TOUCHFILE}"

  fi


  cd "${WORKDIR}"
}

for repo in "$@"; do
  publishLocal "$repo"
done
