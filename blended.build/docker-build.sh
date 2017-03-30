#!/bin/bash 

set -e 

export DOCKER_HOST=127.0.0.1
export DOCKER_PORT=2375 

source ~/.nvm/nvm.sh
nvm use 4.2.6

node --version 

nohup sudo /usr/bin/dockerd -H 127.0.0.1:2375 &> /tmp/docker.out &
cat /tmp/docker.out

docker -H 127.0.0.1:2375 ps -a 

cd ~/project

mvn install -P build,docker,itest -Ddocker.host=$DOCKER_HOST -Ddocker.port=$DOCKER_PORT
