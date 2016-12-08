#!/bin/bash

DOCKER="docker -H=127.0.0.1:4243"

for vm in $($DOCKER ps -aq)
do
  $DOCKER rm -f $vm
done

for image in $($DOCKER images | grep none | awk '{print $3;}')
do
  $DOCKER rmi -f $image
done

