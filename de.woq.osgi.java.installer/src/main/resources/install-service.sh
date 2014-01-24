#!/bin/bash

OLD_DIR=`pwd`
SCRIPT_HOME=`dirname $0`
cd ${SCRIPT_HOME}; SCRIPT_HOME=`pwd`
SERVICE_NAME=$1

cd ${SCRIPT_HOME}/..
KARAF_HOME=`pwd`
JAVA_HOME=${KARAF_HOME}/jre

for jar in $(ls ${SCRIPT_HOME}/*.jar)
do
  APPCP="${APPCP}:${jar}"
done

${JAVA_HOME}/bin/java -cp ${APPCP} -DKARAF_JAVA_HOME=${JAVA_HOME} de.woq.osgi.java.installer.ServiceInstaller -b ${KARAF_HOME} -n ${SERVICE_NAME}

cd ${OLD_DIR}
