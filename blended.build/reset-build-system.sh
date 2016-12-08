#!/bin/bash

rm -Rf ~/.m2/repository
rm -Rf ~/.ivy2/cache

ps -ef | grep zinc | grep java | awk {'print "kill -9 " $2'} | sh
wget http://downloads.typesafe.com/zinc/0.3.11/zinc-0.3.11.tgz
tar -xzf zinc-0.3.11.tgz
sh ./zinc-0.3.11/bin/zinc -start -scala-home /opt/scala


