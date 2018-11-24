#!/bin/bash

set -x

cd `dirname $0`
SCRIPTDIR=$(pwd)

if [ ! -x $SCRIPTDIR/upload-directory.sh ] ; then
  exit 1
fi

if [ ! -x ${SCRIPTDIR}/download-directory.sh ] ; then
  exit 1
fi

if [ ! -x ${SCRIPTDIR}/build-akka.sh ] ; then
  exit 1
fi

if [ ! -x ${SCRIPTDIR}/buildITest.sh ] ; then
  exit 1
fi

if [ ! -x ${SCRIPTDIR}/buildUI.sh ] ; then
  exit 1
fi

if [ ! -x ${SCRIPTDIR}/dropbox_uploader.sh ] ; then
  exit 1
fi

if [ ! -x ${SCRIPTDIR}/itest.sh ] ; then
  exit 1
fi

if [ ! -x ${SCRIPTDIR}/runPublish.sh ] ; then
  exit 1
fi

if [ ! -x ${SCRIPTDIR}/updaterMavenPlugin.sh ] ; then
  exit 1
fi

if [ ! -x ${SCRIPTDIR}/upload-results.sh ] ; then
  exit 1
fi

if [ ! -x ${SCRIPTDIR}/verifyPublish.sh ] ; then
  exit 1
fi

exit 0