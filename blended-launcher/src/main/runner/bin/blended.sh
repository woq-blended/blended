#!/bin/sh

function blended_home() {
  
  home=$1

  if [ x"$BLENDED_HOME" == "x" ]; then
    OLDDIR=$(pwd)
    dir="$(dirname $0)/.."
    cd $dir
    home=$(pwd)
    cd $OLDDIR
  fi  

  echo "$home"
}

export BLENDED_HOME=$(blended_home $BLENDED_HOME)

if [ -z "${LAUNCHER_OPTS}" ]; then
  # go with defaults
  LAUNCHER_OPTS="--profile-lookup $BLENDED_HOME/launch.conf"
  JAVA_OPTS="-Dlogback.configurationFile=$BLENDED_HOME/etc/logback.xml ${JAVA_OPTS}"
fi

RETVAL=2

if [ -n "$DEBUG_PORT" ] ; then
  JAVA_OPTS="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=${DEBUG_PORT},suspend=y ${JAVA_OPTS}"
fi

while [ "x$RETVAL" == "x2" ]; do

java\
 $JAVA_OPTS\
 -cp\
 "${BLENDED_HOME}/etc:${BLENDED_HOME}/lib/*"\
 blended.launcher.Launcher\
 --framework-restart 0\
 ${LAUNCHER_OPTS}\
 "$@" && RETVAL=$? || RETVAL=$?

done

unset BLENDED_HOME
unset RETVAL
