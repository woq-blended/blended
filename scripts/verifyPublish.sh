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
  if [[ $TRAVIS_TAG != "" ]]; then
    echo "Found a tagged version with SNAPSHOT Qualifier, falling back to build without publish"
    export PUBCMD="ciBuild"
  else
    export PUBCMD="ciPublish"
  fi
else 
  if [[ $TRAVIS_TAG = $VERSION ]]; then 
    export PUBCMD="ciRelease"
  else 
    echo "A tag matching the version to be released ($VERSION) is required to push the release to Maven central."
    echo "Falling back to a normal build without publishing."
    export PUBCMD="ciBuild"
  fi
fi

