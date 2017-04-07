#!/bin/bash

DOCKER="docker -H=$DOCKER_HOST:$DOCKER_PORT"

for vm in $($DOCKER ps -aq)
do
  $DOCKER rm -f $vm
done

for image in $($DOCKER images | grep none | awk '{print $3;}')
do
  $DOCKER rmi -f $image
done

