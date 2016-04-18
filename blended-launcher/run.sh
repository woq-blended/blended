#!/bin/sh

TOOLDIR="$(dirname "$0")"

#REPO="${TOOLDIR}/../../mvn/m2repo"
REPO="/Users/andreas/.m2/repository"

BLENDED_HOME=${TOOLDIR}/../blended-launcher-demo/target/classes
BLENDED_VERSION="2.0-SNAPSHOT"

RETVAL=2

while [ "x$RETVAL" == "x2" ]; do

java\
 $JAVA_OPTS\
 -cp\
 "${TOOLDIR}/target/blended.launcher-${BLENDED_VERSION}.jar"\
:"${TOOLDIR}/../blended-updater-config/target/blended.updater.config-${BLENDED_VERSION}.jar"\
:"${REPO}/de/tototec/de.tototec.cmdoption/0.4.2/de.tototec.cmdoption-0.4.2.jar"\
:"${REPO}/org/scala-lang/scala-library/2.10.5/scala-library-2.10.5.jar"\
:"${REPO}/org/osgi/org.osgi.core/5.0.0/org.osgi.core-5.0.0.jar"\
:"${REPO}/ch/qos/logback/logback-core/1.1.3/logback-core-1.1.3.jar"\
:"${REPO}/ch/qos/logback/logback-classic/1.1.3/logback-classic-1.1.3.jar"\
:"${REPO}/com/typesafe/config/1.2.0/config-1.2.0.jar"\
:"${REPO}/commons-daemon/commons-daemon/1.0.15/commons-daemon-1.0.15.jar"\
 -Dfelix.fileinstall.filter=.*cfg\
 -Dfelix.fileinstall.dir=$BLENDED_HOME/etc\
 -Dfelix.fileinstall.bundles.new.start=false\
 -Dfelix.fileinstall.enableConfigSave=true\
 -Dfelix.fileinstall.log.level=3\
 -Dhawtio.authenticationEnabled=false\
 -Dlogback.configurationFile=$BLENDED_HOME/etc/logback.xml\
 -Dblended.home=$BLENDED_HOME\
 blended.launcher.Launcher\
 --framework-restart 0\
 "$@" && RETVAL=$? || RETVAL=$?

done

#:"${REPO}/org/slf4j/slf4j-api/1.7.2/slf4j-api-1.7.2.jar"\
