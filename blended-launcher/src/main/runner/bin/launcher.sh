#!/bin/sh

TOOLDIR="$(dirname $(readlink -f "$0"))/.."

RETVAL=2

if [ -n "$DEBUG_PORT" ] ; then
  JAVA_OPTS="-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=${DEBUG_PORT},suspend=y ${JAVA_OPTS}"
fi

while [ "x$RETVAL" == "x2" ]; do

java\
 $JAVA_OPTS\
 -cp\
 "${TOOLDIR}/lib/*"\
 blended.launcher.Launcher\
 --framework-restart 0\
 ${LAUNCHER_OPTS}\
 "$@" && RETVAL=$? || RETVAL=$?

done

unset TOOLDIR
unset RETVAL
