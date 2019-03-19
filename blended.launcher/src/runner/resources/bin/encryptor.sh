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
ENC_CP="${BLENDED_HOME}/lib/blended.security.crypto_@scala.binary.version@-@blended.security.crypto.version@.jar:\
${BLENDED_HOME}/lib/scala-library-@scala.library.version@.jar:\
${BLENDED_HOME}/lib/de.tototec.cmdoption-@cmdoption.version@.jar\
"

exec ${JAVA_HOME}/bin/java\
 -cp\
 "${ENC_CP}"\
 blended.security.crypto.BlendedEncryptor\
 "$@"
