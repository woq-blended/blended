#!/bin/sh

function setenv() {

  if [ -f $BLENDED_HOME/bin/setenv ] ; then
    . $BLENDED_HOME/bin/setenv
  fi
}

function blended_home() {
  
  home=$1

  if [ x"$BLENDED_HOME" == "x" ] ; then
    OLDDIR=$(pwd)
    dir="$(dirname $0)/.."
    cd $dir
    home=$(pwd)
    cd $OLDDIR
  fi  

  echo "$home"
}

export BLENDED_HOME=$(blended_home $BLENDED_HOME)
setenv

cd $BLENDED_HOME

LAUNCHER_OPTS="--profile-lookup $BLENDED_HOME/launch.conf --init-profile-props"
JAVA_OPTS="-Dlogback.configurationFile=${BLENDED_HOME}/etc/logback.xml ${JAVA_OPTS}"
JAVA_OPTS="-Dlog4j.configurationFile=${BLENDED_HOME}/etc/log4j.xml -Dblended.home=${BLENDED_HOME} ${JAVA_OPTS}"
JAVA_OPTS="-Dsun.net.client.defaultConnectTimeout=500 -Dsun.net.client.defaultReadTimeout=500 ${JAVA_OPTS}"

if [ -n "$DEBUG_PORT" ] ; then
  JAVA_OPTS="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=${DEBUG_PORT},suspend=y ${JAVA_OPTS}"
fi

# colun-separated
OUTER_CP="${BLENDED_HOME}/lib/*"
# semicolon-separated
INNER_CP="\
${BLENDED_HOME}/etc;\
${BLENDED_HOME}/lib/blended.launcher-1.2-SNAPSHOT.jar;\
${BLENDED_HOME}/lib/config-1.2.1.jar;\
${BLENDED_HOME}/lib/org.osgi.core-5.0.0.jar;\
${BLENDED_HOME}/lib/blended.updater.config-1.2-SNAPSHOT.jar;\
${BLENDED_HOME}/lib/de.tototec.cmdoption-0.4.2.jar;\
${BLENDED_HOME}/lib/scala-library-2.10.5.jar;\
${BLENDED_HOME}/lib/slf4j-api-1.7.12.jar;\
${BLENDED_HOME}/lib/logback-core-1.1.3.jar;\
${BLENDED_HOME}/lib/logback-classic-1.1.3.jar;\
"

$JAVA_HOME/bin/java -version

exec ${JAVA_HOME}/bin/java\
 $JAVA_OPTS\
 -cp\
 "${OUTER_CP}"\
 blended.launcher.jvmrunner.JvmLauncher\
 start\
 "-cp=${INNER_CP}"\
 -- \
 blended.launcher.Launcher \
 --framework-restart 0\
 ${LAUNCHER_OPTS}\
 "$@"