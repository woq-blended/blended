#!/usr/bin/env bash

dirs=$(find . -name pom.scala -exec dirname {} \; | grep -v "target/scalamodel")

for dir in $dirs; do

  if [ -e "${dir}/pom.scala" ]; then
    if [ -e "${dir}/pom.xml" ]; then
      echo "Deleting ${dir}/pom.xml"
      rm "${dir}/pom.xml"
    fi
  fi

done

unset cont

