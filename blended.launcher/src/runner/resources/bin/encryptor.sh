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
cd $BLENDED_HOME

# column-separated
ENC_CP="${BLENDED_HOME}/lib/*"

exec ${JAVA_HOME}/bin/java\
 -cp\
 "${ENC_CP}"\
 blended.security.crypto.Encryptor\
 "$@"
