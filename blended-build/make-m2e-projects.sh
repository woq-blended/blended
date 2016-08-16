#!/bin/bash

set -e

blendedVersion="2.0-SNAPSHOT"
classesDir="target-eclipse/classes"
testClassesDir="target-eclipse/test-classes"
libsDir="target-eclipse/libs"

projects="blended-activemq-brokerstarter \
blended-akka \
blended-akka-itest \
blended-camel-utils \
blended-container-context \
blended-container-registry \
blended-domino \
blended-hawtio \
blended-hawtio-login \
blended-itestsupport \
blended-jms-utils \
blended-jmx \
blended-jolokia \
blended-launcher \
blended-mgmt-base \
blended-mgmt-agent \
blended-mgmt-rest \
blended-neo4j-api \
blended-parent \
blended-persistence \
blended-persistence-orient \
blended-persistence-neo4j \
blended-persistence-h2 \
blended-samples \
blended-spray \
blended-spray-api \
blended-testsupport \
blended-updater \
blended-updater-config \
blended-updater-tools \
blended-updater-remote \
blended-updater-maven-plugin \
blended-launcher-features \
blended-launcher-demo \
blended-demo-mgmt \
blended-util \
blended-security \
blended-security-boot"

genForProjects="$projects"

if [ -n "$*" ]; then
  echo "Generating for selected projects: $*"
  genForProjects="$*"
fi

for project in $genForProjects; do

  echo ""
  echo "*******************************************************"
  echo "* Project: $project"
  echo "* "

  cd "$project"

  cat > .project << EOF
<?xml version="1.0" encoding="UTF-8"?>
<projectDescription>
        <name>$project</name>
        <comment></comment>
        <projects>
        </projects>
        <buildSpec>
                <buildCommand>
                        <name>org.scala-ide.sdt.core.scalabuilder</name>
                        <arguments>
                        </arguments>
                </buildCommand>
<buildCommand>
                        <name>org.eclipse.m2e.core.maven2Builder</name>
                        <arguments>
                        </arguments>
                </buildCommand>
        </buildSpec>
        <natures>
                <nature>org.scala-ide.sdt.core.scalanature</nature>
                <nature>org.eclipse.jdt.core.javanature</nature>
                <nature>org.eclipse.m2e.core.maven2Nature</nature>
        </natures>
</projectDescription>
EOF

  cat > .classpath << EOF
<?xml version="1.0" encoding="UTF-8"?>
<classpath>
EOF

  for path in src/main/scala src/main/java src/main/binaryResources src/main/resources; do
    if [ -e "$path" ]; then
      cat >> .classpath << EOF
        <classpathentry kind="src" path="$path"/>
EOF
    fi
  done

  for path in src/test/scala src/test/java src/test/binaryResources src/test/resources; do
    if [ -e "$path" ]; then
      cat >> .classpath << EOF
        <classpathentry kind="src" output="${testClassesDir}" path="$path"/>
EOF
    fi
  done

  cat >> .classpath << EOF
        <classpathentry kind="output" path="${classesDir}"/>
        <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.7"/>
EOF

  mkdir -p .settings

  cat > .settings/org.eclipse.core.resources.prefs << EOF
eclipse.preferences.version=1
encoding/<project>=UTF-8
EOF

  cat > .settings/org.eclipse.core.runtime.prefs << EOF
eclipse.preferences.version=1
line.separator=\n
EOF

  cat > .settings/org.eclipse.jdt.core.prefs << EOF
eclipse.preferences.version=1
org.eclipse.jdt.core.compiler.codegen.inlineJsrBytecode=enabled
org.eclipse.jdt.core.compiler.codegen.targetPlatform=1.7
org.eclipse.jdt.core.compiler.compliance=1.7
org.eclipse.jdt.core.compiler.problem.assertIdentifier=error
org.eclipse.jdt.core.compiler.problem.enumIdentifier=error
org.eclipse.jdt.core.compiler.problem.forbiddenReference=warning
org.eclipse.jdt.core.compiler.source=1.7
EOF

  cat > .settings/org.scala-ide.sdt.core.prefs << EOF
P=
Xcheckinit=false
Xdisable-assertions=false
Xelide-below=-2147483648
Xexperimental=false
Xfatal-warnings=false
Xfuture=false
Xlog-implicits=false
Xno-uescape=false
Xplugin=
Xplugin-disable=
Xplugin-require=
Xpluginsdir=misc/scala-devel/plugins
Ypresentation-debug=false
Ypresentation-delay=0
Ypresentation-log=
Ypresentation-replay=
Ypresentation-verbose=false
apiDiff=false
compileorder=Mixed
deprecation=false
eclipse.preferences.version=1
explaintypes=false
feature=false
g=vars
nameHashing=false
no-specialization=false
nowarn=false
optimise=false
recompileOnMacroDef=true
relationsDebug=false
scala.compiler.installation=2.11
scala.compiler.sourceLevel=2.11
scala.compiler.useProjectSettings=true
stopBuildOnError=true
target=jvm-1.6
unchecked=false
useScopesCompiler=true
verbose=false
withVersionClasspathValidator=false
EOF

    cat >> .classpath << EOF
    <classpathentry kind="con" path="org.eclipse.m2e.MAVEN2_CLASSPATH_CONTAINER" />
EOF


    cat >> .classpath << EOF
</classpath>
EOF

  cd ..

done
