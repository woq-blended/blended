#!/bin/sh

function setenv() {

  if [ -f "$BLENDED_HOME/bin/setenv" ] ; then
    . "$BLENDED_HOME/bin/setenv"
  fi
}

function blended_home() {

  home=$1

  if [ "x$BLENDED_HOME" == "x" ] ; then
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
  # we provide 2-minute default so that remote servers can clean up as well,
  # when no such settings was defined before
  RESTART_DELAY=120
fi

# Whether to start the container in interactive mode
if [ -z "${INTERACTIVE}" ]; then
  INTERACTIVE=false
fi

if [ "${INTERACTIVE}" == "true" ]; then
  EXTRA_START_BUNDLES="-jvmOpt=-Dblended.laucher.startbundles=org.apache.felix.gogo.runtime,org.apache.felix.gogo.shell,org.apache.felix.gogo.command"
else
  EXTRA_START_BUNDLES=""
fi

LAUNCHER_OPTS="--profile-lookup $BLENDED_HOME/launch.conf --init-container-id"
if [ "x$BLENDED_STRICT" != "x" ] ; then
  LAUNCHER_OPTS="$LAUNCHER_OPTS --strict"
fi

LOGBACK_CONFIG_SETTING="-Dlogback.configurationFile=${BLENDED_HOME}/etc/logback.xml"

# Options for the service daemen JVM (outer) with controls the container JVM
JAVA_OPTS="${JAVA_OPTS} -Xmx24m"
JAVA_OPTS="${JAVA_OPTS} ${LOGBACK_CONFIG_SETTING}"
JAVA_OPTS="${JAVA_OPTS} -Dblended.home=${BLENDED_HOME}"

# Options for the container JVM (inner) started/managed by the service daemon JVM
# Use prefix "-jvmOpt=" to mark each JVM option to be passed to the container JVM

CONTAINER_JAVA_OPTS="${CONTAINER_JAVA_OPTS} -jvmOpt=-Dsun.net.client.defaultConnectTimeout=500"
CONTAINER_JAVA_OPTS="${CONTAINER_JAVA_OPTS} -jvmOpt=-Dsun.net.client.defaultReadTimeout=500"
CONTAINER_JAVA_OPTS="${CONTAINER_JAVA_OPTS} -jvmOpt=${LOGBACK_CONFIG_SETTING}"
CONTAINER_JAVA_OPTS="${CONTAINER_JAVA_OPTS} -jvmOpt=-Dblended.home=${BLENDED_HOME}"
CONTAINER_JAVA_OPTS="${CONTAINER_JAVA_OPTS} ${EXTRA_START_BUNDLES}"

# Enable this when you need to debug SSL issues
# CONTAINER_JAVA_OPTS="${CONTAINER_JAVA_OPTS} -jvmOpt=-Djavax.net.debug=ssl"

if [ -n "$DEBUG_PORT" ] ; then
  if [ -n "$DEBUG_WAIT" ] ; then
    MY_DEBUG_WAIT="y"
  else
    MY_DEBUG_WAIT="n"
  fi
  CONTAINER_JAVA_OPTS="${CONTAINER_JAVA_OPTS} -jvmOpt=-agentlib:jdwp=server=y,transport=dt_socket,address=${DEBUG_PORT},suspend=${MY_DEBUG_WAIT}"
  unset MY_DEBUG_WAIT
fi

if [ -n "$PROFILE_PORT" ] ; then
 if [ -n "$PROFILE_WAIT" ] ; then
   MY_PROFILE_WAIT=""
  else
   MY_PROFILE_WAIT=",nowait"
 fi
 CONTAINER_JAVA_OPTS="${CONTAINER_JAVA_OPTS} -jvmOpt=-agentpath:${BLENDED_HOME}/lib/linux-x64/libjprofilerti.so=port=${PROFILE_PORT}${MY_PROFILE_WAIT}"
 UNSET MY_PROFILE_WAIT
fi

# column-separated
OUTER_CP="${BLENDED_HOME}/lib/*"
# semicolon-separated
INNER_CP="\
${BLENDED_HOME}/etc;\
${BLENDED_HOME}/lib/blended.launcher_@scala.binary.version@-@blended.launcher.version@.jar;\
${BLENDED_HOME}/lib/config-@typesafe.config.version@.jar;\
${BLENDED_HOME}/lib/org.osgi.core-@org.osgi.core.version@.jar;\
${BLENDED_HOME}/lib/blended.updater.config_@scala.binary.version@-@blended.updater.config.version@.jar;\
${BLENDED_HOME}/lib/blended.util.logging_@scala.binary.version@-@blended.util.logging.version@.jar;\
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
 "-interactive=${INTERACTIVE}"\
 -- \
 blended.launcher.Launcher \
 --framework-restart 0\
 ${LAUNCHER_OPTS}\
 "$@"
