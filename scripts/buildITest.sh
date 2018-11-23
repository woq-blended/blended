#!/bin/bash

git clone https://github.com/woq-blended/blended.itestsupport.git blendedITest
cd blendedITest
git checkout master
sbt publishM2
cd ..