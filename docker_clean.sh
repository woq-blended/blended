#!/bin/bash

for vm in $(docker ps -aq)
do
  docker rm -f $vm
done

for image in $(docker images | grep none | awk '{print $3;}')
do
  docker rmi -f $image 
done
