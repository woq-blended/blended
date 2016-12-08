#!/bin/bash

ZINC_VERSION=0.3.11

rm -Rf ~/.m2/repository
rm -Rf ~/.ivy2/cache

ps -ef | grep zinc | grep java | awk {'print "kill -9 " $2'} | sh
wget http://downloads.typesafe.com/zinc/${ZINC_VERSION}/zinc-${ZINC_VERSION}.tgz
tar -xzf zinc-${ZINC_VERSION}.tgz
rm -f zinc-${ZINC_VERSION}.tgz

sh ./zinc-${ZINC_VERSION}/bin/zinc -start -scala-home /opt/scala

exit 0


