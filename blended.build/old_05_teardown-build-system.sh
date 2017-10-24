#!/bin/bash

SCRIPT_DIR=`dirname "$0"`

ps -ef | grep zinc | grep java | awk {'print "kill -9 " $2'} | sh
sh ${SCRIPT_DIR}/docker_clean.sh
