#!/bin/sh
cd blended-parent
mvn clean install
cd..
mvn clean install -P parent | grep -v "Download" | grep -v "longer than 100"
mvn clean install -P build,assembly | grep -v "Download" | grep -v "longer than 100"
