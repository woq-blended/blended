#!/bin/sh
mvn clean install -P parent | grep -v "Download" | grep -v "longer than 100"
mvn clean install -P build,assembly | grep -v "Download" | grep -v "longer than 100"
