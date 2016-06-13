#!/usr/bin/env bash

dirs=$(find -path "*/target/scalamodel/pom.scala" -prune -o -name pom.scala -exec dirname {} \;)

for dir in $dirs; do

  cont="n"

  if [ "${dir}/pom.xml" -ot "${dir}/pom.scala" ]; then
    cont="y"

#    echo -n "${dir}/pom.xml is newer than ${dir}/pom.scala! Continue anyway [y/N]?"
#    read cont

  fi

  if [ "$cont" == "y" ]; then

    echo "Converting ${dir}/pom.scala ->  ${dir}/pom.xml"
    (cd ${dir} && mvn -N io.takari.polyglot:polyglot-translate-plugin:0.1.15:translate -Dinput=pom.scala -Doutput=pom.xml)

  fi

done

unset cont
