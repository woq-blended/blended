#!/bin/bash
git clone https://github.com/woq-blended/akka.git akka 
cd akka 
git checkout osgi
sbt publishM2 > akka-sbt.out
cd ..
