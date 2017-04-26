# The basic image to run Blended containers
FROM debian:latest

MAINTAINER Andreas Gies 

#wget -c --header "Cookie: oraclelicense=accept-securebackup-cookie" http://download.oracle.com/otn-pub/java/jdk/8u131-b11/d54c1d3a095b4ff2b6607d096fa80163/jdk-8u131-linux-x64.tar.gz

# Installation section
ADD files/software/jdk-8u131-linux-x64.tar.gz /opt
ADD https://downloads.lightbend.com/scala/2.11.11/scala-2.11.11.tgz /opt
ADD https://github.com/sbt/sbt/releases/download/v0.13.15/sbt-0.13.15.tgz /opt
ADD http://ftp.fau.de/apache/maven/maven-3/3.5.0/binaries/apache-maven-3.5.0-bin.tar.gz /opt
ADD http://downloads.typesafe.com/zinc/0.3.13/zinc-0.3.13.tgz /opt
ADD files/profile.d /etc/profile.d

RUN cd /opt \
  && wget -c --header "Cookie: oraclelicense=accept-securebackup-cookie" http://download.oracle.com/otn-pub/java/jdk/8u131-b11/d54c1d3a095b4ff2b6607d096fa80163/jdk-8u131-linux-x64.tar.gz \
  && tar -xvzf jdk-8u131-linux-x64.tar.gz  

RUN ln -s /opt/jdk1.8.0_131 /opt/java \
 && ln -s /opt/scala-2.11.11 /opt/scala \
 && ln -s /opt/sbt-launcher-packaging-0.13.15 /opt/sbt \
 && ln -s /opt/apache-maven-3.5.0 /opt/maven \
 && ln -s /opt/zinc-0.3.13 /opt/zinc 

