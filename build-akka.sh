#!/bin/bash

if [ ! -f$HOME/.m2/repository/com/typesafe/akka/akka-actor_2.12/2.5.17.1/akka-actor_2.12-2.5.17.1.jar ]
then 
  git clone https://github.com/woq-blended/akka.git akka 
  cd akka 
  git checkout osgi
  sbt publishM2 > akka-sbt.out
  cd ..
fi

