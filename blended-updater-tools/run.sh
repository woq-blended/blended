#!bin/sh

set -e

TOOLDIR="$(dirname "$0")"

#M2REPO="${TOOLDIR}/../../mvn/m2repo"
M2REPO="/Users/andreas/.m2/repository"

BLENDED_VERSION="2.0-SNAPSHOT"

java\
 -classpath\
 "${TOOLDIR}/target/classes"\
:"$M2REPO/com/typesafe/config/1.2.0/config-1.2.0.jar"\
:"$M2REPO/de/wayofquality/blended/blended.updater.config/${BLENDED_VERSION}/blended.updater.config-${BLENDED_VERSION}.jar"\
:"$M2REPO/de/tototec/de.tototec.cmdoption/0.4.2/de.tototec.cmdoption-0.4.2.jar"\
:"$M2REPO/org/scala-lang/scala-library/2.10.5/scala-library-2.10.5.jar"\
 blended.updater.tools.configbuilder.RuntimeConfigBuilder\
 "$@"

