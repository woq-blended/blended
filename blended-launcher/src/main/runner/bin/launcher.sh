#!/bin/sh

OLDDIR=$(pwd)
TOOLDIR="$(dirname $0)/.."
cd $TOOLDIR
export TOOLDIR=$(pwd)
cd $OLDDIR

LAUNCHER_OPTS="--profile-lookup "$TOOLDIR/launch.conf""

RETVAL=2
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
