# The basic image to run Blended containers
FROM debian:latest

MAINTAINER Andreas Gies 

ARG USER=blended
ARG GROUP=blended 
ARG UID=2000
ARG GID=2000

# Installation section
ADD files/profile.d /etc/profile.d

RUN groupadd -g ${GID} ${GROUP} ; useradd -d /home/${USER} -s /bin/bash -g ${GROUP} -m ${USER} \
 && apt-get -y -q update \
 && apt-get -y -q install \
    wget 

RUN cd /opt \
 && wget -q -c -O - --header "Cookie: oraclelicense=accept-securebackup-cookie" http://download.oracle.com/otn-pub/java/jdk/8u131-b11/d54c1d3a095b4ff2b6607d096fa80163/jdk-8u131-linux-x64.tar.gz | tar -xzf - \
 && wget -q -c -O - https://github.com/sbt/sbt/releases/download/v0.13.15/sbt-0.13.15.tgz | tar -xzf - \
 && wget -q -c -O - http://ftp.fau.de/apache/maven/maven-3/3.5.0/binaries/apache-maven-3.5.0-bin.tar.gz | tar -xzf - \
 && wget -q -c -O - http://downloads.typesafe.com/zinc/0.3.13/zinc-0.3.13.tgz | tar -xzf - 

RUN ln -s /opt/jdk1.8.0_131 /opt/java \
 && ln -s /opt/sbt-launcher-packaging-0.13.15 /opt/sbt \
 && ln -s /opt/apache-maven-3.5.0 /opt/maven \
 && ln -s /opt/zinc-0.3.13 /opt/zinc  \
 && PATH=/opt/java/bin:$PATH; /opt/sbt/bin/sbt about 

USER ${USER}

RUN wget -q -c -O - https://raw.githubusercontent.com/creationix/nvm/v0.33.1/install.sh | /bin/bash; /bin/bash -l -c "source $HOME/.nvm/nvm.sh ; nvm install 4.2"

ENTRYPOINT ["/bin/bash", "-l"]


