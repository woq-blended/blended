#!/usr/bin/env bash

DOCKER_HOST=127.0.01
DOCKER_PORT=2375

function mvnRepo {

  echo "$1/mvnRepo"
}

function ivyRepo {

  echo "$1/ivyRepo"
}

function execMaven {

  local m2Repo=$1
  shift 1

  local ivyRepo=$1
  shift 1

  local profiles=$3
  shift 1

  mvn $* -P $profiles $profiles -Dmaven.repo.local=$m2Repo -Divy2.repo.local=$ivyRepo -Ddocker.host=$DOCKER_HOST -Ddocker.port=$DOCKER_PORT
  exit $*
}