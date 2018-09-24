#!/bin/bash

# Just get the current version from the text file 
VERSION=$(cat version.txt)

# Check if it ends on SNAPSHOT
if [[ $VERSION =~ ^.*SNAPSHOT$ ]]; then 
  ISSNAPSHOT=1
else 
  ISSNAPSHOT=0
fi

# if it ends on SNAPSHOT, we won't check the git tag and set 
# the publishCmd to ciPublish
# If it is not a SNAPSHOT, we will check if the git tag has the 
# same name as the version to be released. If so, we will set 
# the publishCmd to ciRelease, otherwise we will break the build.

if [[ ISSNAPSHOT -eq 1 ]]; then 
  export PUBCMD="ciPublish"
else 
  if [[ $TRAVIS_TAG = $VERSION ]]; then 
    export PUBCMD="ciRelease"
  else 
    export PUBCMD=
    echo "A tag matching the version to be released ($VERSION) is required to push the release to Maven central."
    exit 1
  fi
fi

