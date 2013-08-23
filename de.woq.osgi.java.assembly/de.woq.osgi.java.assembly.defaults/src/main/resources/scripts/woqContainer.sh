#!/bin/bash
#
# Copyright 2013, WoQ - Way of Quality UG(mbH)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

OLD_PWD=`pwd`
WOQ_HOME=`dirname $0`/..
cd ${WOQ_HOME}
WOQ_HOME=`pwd`
cd ${OLD_PWD}

JAVA=${JAVA_HOME}/bin/java

if [[ ! -e ${JAVA} ]]; then
  JAVA=`which java`
fi

if [[ ! -e ${JAVA} ]]; then
  JAVA=${WOQ_HOME}/jre/bin/java
fi

if [[ ! -e ${JAVA} ]]; then
  if [[ ! -z `echo ${OSTYPE} | grep "darwin"` ]]; then
    JAVA=/System/Library/Frameworks/JavaVM.framework/Home/bin/java
  fi  
fi

if [[ ! -e ${JAVA} ]]; then
  echo ""
  echo "Unable to locate a Java Virtual Machine. The container framework looks"
  echo "in the following locations and will take the first available JVM it finds:"
  echo ""
  echo " 1 - A JVM referenced by the JAVA_HOME variable [${JAVA_HOME}]"
  echo ""
  echo " 2 - A JVM that is available from the current PATH environment variable [${PATH}]"
  echo ""
  echo " 3 - A JVM that is loacated in the JRE directory of the Whiteboard installation [${WHITEBOARD_HOME}/jre]"
  echo ""
  echo " 4 - On OS X in [/System/Library/Frameworks/JavaVM.framework/Home/bin]"
  echo ""
  echo "Please make a valid Java Virtual machine available in one of these locations."
  echo ""
  exit 1
fi

APPCP=${WOQ_HOME}/config

for jar in $(ls $WOQ_HOME/lib/*.jar)
do
  APPCP=$APPCP:${jar}
done

echo "WOQ Home directory is [${WOQ_HOME}]."
${JAVA} -version

${JAVA} -classpath $APPCP de.woq.osgi.java.container.WOQContainer -jvm.property.woq.home ${WOQ_HOME} $1

exit $?
