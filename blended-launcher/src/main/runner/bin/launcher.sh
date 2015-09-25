#!/bin/sh

OLDDIR=$(pwd)
BLENDED_HOME="$(dirname $0)/.."
cd $BLENDED_HOME
export BLENDED_HOME=$(pwd)
cd $OLDDIR

LAUNCHER_OPTS="--profile-lookup "$BLENDED_HOME/launch.conf""

RETVAL=2
RETVAL=2

if [ -n "$DEBUG_PORT" ] ; then
  JAVA_OPTS="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=${DEBUG_PORT},suspend=y ${JAVA_OPTS}"
fi

while [ "x$RETVAL" == "x2" ]; do

java\
 $JAVA_OPTS\
 -cp\
 "${BLENDED_HOME}/lib/*"\
 blended.launcher.Launcher\
 --framework-restart 0\
 ${LAUNCHER_OPTS}\
 "$@" && RETVAL=$? || RETVAL=$?

done

unset BLENDED_HOME
unset RETVAL
