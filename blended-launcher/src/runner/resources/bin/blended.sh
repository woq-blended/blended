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

# JVM Restart Delay in seconds
if [ -z "${RESTART_DELAY}" ]; then
  # we provide a zero-default, when no such settings was defined before
  RESTART_DELAY=0
fi

LAUNCHER_OPTS="--profile-lookup $BLENDED_HOME/launch.conf --init-profile-props"

# Options for the service daemen JVM (outer) with controls the container JVM
JAVA_OPTS="${JAVA_OPTS} -Xmx24m"
JAVA_OPTS="${JAVA_OPTS} -Dlogback.configurationFile=${BLENDED_HOME}/etc/logback.xml"
JAVA_OPTS="${JAVA_OPTS} -Dlog4j.configurationFile=${BLENDED_HOME}/etc/log4j.xml -Dblended.home=${BLENDED_HOME}"
JAVA_OPTS="${JAVA_OPTS} -Dsun.net.client.defaultConnectTimeout=500 -Dsun.net.client.defaultReadTimeout=500"

# Options for the container JVM (inner) started/managed by the service daemon JVM
# Use prefix "-jvmOpt=" to mark JVM options for the container JVM
#CONTAINER_JAVA_OPTS="${CONTAINER_JAVA_OPTS} -jvmOpt=-Xmx1024m"

if [ -n "$DEBUG_PORT" ] ; then
  CONTAINER_JAVA_OPTS="${CONTAINER_JAVA_OPTS} -jvmOpt=-Xdebug -jvmOpt=-Xrunjdwp:server=y,transport=dt_socket,address=${DEBUG_PORT},suspend=y ${JAVA_OPTS}"
fi

# colun-separated
OUTER_CP="${BLENDED_HOME}/lib/*"
# semicolon-separated
INNER_CP="\
${BLENDED_HOME}/etc;\
${BLENDED_HOME}/lib/blended.launcher-@blended.launcher.version@.jar;\
${BLENDED_HOME}/lib/config-@typesafe.config.version@.jar;\
${BLENDED_HOME}/lib/org.osgi.core-@org.osgi.core.version@.jar;\
${BLENDED_HOME}/lib/blended.updater.config-@blended.updater.config.version@.jar;\
${BLENDED_HOME}/lib/de.tototec.cmdoption-@cmdoption.version@.jar;\
${BLENDED_HOME}/lib/scala-library-@scala.library.version@.jar;\
${BLENDED_HOME}/lib/slf4j-api-@slf4j.version@.jar;\
${BLENDED_HOME}/lib/logback-core-@logback.version@.jar;\
${BLENDED_HOME}/lib/logback-classic-@logback.version@.jar;\
"

$JAVA_HOME/bin/java -version

exec ${JAVA_HOME}/bin/java\
 $JAVA_OPTS\
 -cp\
 "${OUTER_CP}"\
 blended.launcher.jvmrunner.JvmLauncher\
 start\
 ${CONTAINER_JAVA_OPTS}\
 "-cp=${INNER_CP}"\
 "-restartDelay=${RESTART_DELAY}"\
 -- \
 blended.launcher.Launcher \
 --framework-restart 0\
 ${LAUNCHER_OPTS}\
 "$@"
