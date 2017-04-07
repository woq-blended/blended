#!/bin/bash 

set -e 
set -x 

export DOCKER_HOST=127.0.0.1
export DOCKER_PORT=2375 

SCRIPT_DIR=$(dirname $0)

source ~/.nvm/nvm.sh
nvm use 4.2.6

node --version 

sudo /usr/bin/dockerd -H 127.0.0.1:2375 &

cd ~/project

mvn clean -P build | grep -v -i "downloading" | grep -v -i "CheckForNull" | grep -v -i "longer than 100 characters"

mvn install -P build,docker,itest -Ddocker.host=$DOCKER_HOST -Ddocker.port=$DOCKER_PORT | grep -v -i "downloading" | grep -v -i "CheckForNull" | grep -v -i "longer than 100 characters"
