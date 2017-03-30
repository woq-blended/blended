#!/bin/bash 

set -e 
set -x

export DOCKER_HOST=127.0.0.1
export DOCKER_PORT=2375 

source ~/.nvm/nvm.sh
nvm use 4.2.6

node --version 

# nohup sudo /usr/bin/dockerd -H 127.0.0.1:2375 &> /tmp/docker.out &

cd ~/project

mvn install -P build,docker,itest -Ddocker.host=$DOCKER_HOST -Ddocker.port=$DOCKER_PORT
