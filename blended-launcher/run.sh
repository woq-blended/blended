#!/bin/sh

TOOLDIR="$(dirname "$0")"

REPO="${TOOLDIR}/../../mvn/m2repo"

RETVAL=2

while [ "x$RETVAL" == "x2" ]; do

java\
 $JAVA_OPTS\
 -cp\
 "${TOOLDIR}/target/blended.launcher-1.2-SNAPSHOT.jar"\
:"${TOOLDIR}/../blended-updater-config/target/blended.updater.config-1.2-SNAPSHOT.jar"\
:"${REPO}/de/tototec/de.tototec.cmdoption/0.4.2/de.tototec.cmdoption-0.4.2.jar"\
:"${REPO}/org/scala-lang/scala-library/2.10.5/scala-library-2.10.5.jar"\
:"${REPO}/org/osgi/org.osgi.core/5.0.0/org.osgi.core-5.0.0.jar"\
:"${REPO}/org/slf4j/slf4j-api/1.7.2/slf4j-api-1.7.2.jar"\
:"${REPO}/ch/qos/logback/logback-core/1.1.3/logback-core-1.1.3.jar"\
:"${REPO}/ch/qos/logback/logback-classic/1.1.3/logback-classic-1.1.3.jar"\
:"${REPO}/com/typesafe/config/1.2.0/config-1.2.0.jar"\
:"${REPO}/commons-daemon/commons-daemon/1.0.15/commons-daemon-1.0.15.jar"\
 blended.launcher.Launcher\
 --framework-restart 0\
 --profile-lookup ${TOOLDIR}/launch-profile.conf\
 "$@" && RETVAL=$? || RETVAL=$?

done
