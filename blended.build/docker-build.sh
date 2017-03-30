#!/bin/bash 

set -e 

export DOCKER_HOST=127.0.0.1
export DOCKER_PORT=2375 

source /home/blended/.nvm/nvm.sh
nvm use 4.2.6

node --version 

nohup sudo /usr/bin/dockerd -H 127.0.0.1:2375 &> /tmp/docker.out &

cd ~/project 

mvn install -P build,docker,itest -Ddocker.host=$DOCKER_HOST -Ddocker.port=$DOCKER_PORT > /tmp/mvn.out 2>&1 /tmp/mvn.out & ; $MVN_RC=$? 
tail -f /tmp/mvn.out | grep -v -i "download" | grep -v -i "CheckForNull" | grep -v -i "longer than 100 characters"

exit $MVN_RC
