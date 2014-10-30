#!/bin/sh
mvn clean install -P parent | grep -v "^Download"
mvn clean install -P build,assembly | grep -v "^Download"
