#/bin/sh

mkdir -p "../3rdparty"

DIR=scalajs-react-components
BRANCH=selectable-table
VERSION=0.8.1

(
  cd ../3rdparty &&
  rm -rf "${DIR}" &&
  git clone https://github.com/woq-blended/scalajs-react-components.git "${DIR}" &&
  cd "${DIR}" &&
  git checkout "${BRANCH}" &&
  sbt -batch clean "+ publishLocal"
  mvn install:install-file -DartifactId=scalajs-react-components_sjs0.6_2.11 -DgroupId=com.olvind -Dversion=${VERSION} -DgeneratePom=true -Dfile=core/target/scala-2.11/scalajs-react-components_sjs0.6_2.11-${VERSION}.jar -Dpackaging=jar
  mvn install:install-file -DartifactId=scalajs-react-components_sjs0.6_2.12 -DgroupId=com.olvind -Dversion=${VERSION} -DgeneratePom=true -Dfile=core/target/scala-2.12/scalajs-react-components_sjs0.6_2.12-${VERSION}.jar -Dpackaging=jar
)
