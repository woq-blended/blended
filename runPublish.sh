#!/bin/bash

. ./verifyPublish.sh

if [[ $PUBCMD != "" ]]; then
  sbt $PUBCMD
fi
