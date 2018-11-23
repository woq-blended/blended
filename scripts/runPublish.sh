#!/bin/bash

. $TRAVIS_BUILD_DIR/scripts/verifyPublish.sh

if [[ $PUBCMD != "" ]]; then
  sbt $PUBCMD
fi
