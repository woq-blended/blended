#!/bin/sh

set -e

REPO=../../mvn/m2repo

java $JAVA_OPTS -cp target/blended.launcher-1.2-SNAPSHOT.jar\
:../blended-updater-config/target/blended.updater.config-1.2-SNAPSHOT.jar\
:${REPO}/de/tototec/de.tototec.cmdoption/0.4.2/de.tototec.cmdoption-0.4.2.jar\
:${REPO}/org/scala-lang/scala-library/2.10.5/scala-library-2.10.5.jar\
:${REPO}/org/osgi/org.osgi.core/5.0.0/org.osgi.core-5.0.0.jar\
:${REPO}/org/slf4j/slf4j-api/1.7.2/slf4j-api-1.7.2.jar\
:${REPO}/com/typesafe/config/1.2.0/config-1.2.0.jar\
:${REPO}/commons-daemon/commons-daemon/1.0.15/commons-daemon-1.0.15.jar \
blended.launcher.Launcher "$@"
