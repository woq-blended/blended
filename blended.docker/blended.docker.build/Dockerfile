# The basic image to run Blended container builds from Jenkins pipelines
# Jenkins expects a jenkins account with user/pw: jenkins/jenkins which must have the same
# UID/GID as on the host, therefore we set the UID/GID via ARG

FROM debian:latest

MAINTAINER Andreas Gies 

ARG USER=jenkins
ARG GROUP=jenkins
ARG UID=125
ARG GID=131

ARG JAVA_HOME=/opt/java
ARG M2_HOME=/opt/maven
ARG SBT_HOME=/opt/sbt

# Installation section
ENV JAVA_HOME=${JAVA_HOME} \
    M2_HOME=${M2_HOME} \
    SBT_HOME=${SBT_HOME} \
    PATH=${JAVA_HOME}/bin:${M2_HOME}/bin:${SBT_HOME}/bin:$PATH

RUN groupadd -g ${GID} ${GROUP} ; useradd -u ${UID} -d /home/${USER} -s /bin/bash -g ${GROUP} -m ${USER} \
 && apt-get -y -q update \
 && apt-get -y -q install \
    openssh-server \
    wget

RUN cd /opt \
 && wget -q -c -O - --header "Cookie: oraclelicense=accept-securebackup-cookie" http://download.oracle.com/otn-pub/java/jdk/8u131-b11/d54c1d3a095b4ff2b6607d096fa80163/jdk-8u131-linux-x64.tar.gz | tar -xzf - \
 && wget -q -c -O - https://github.com/sbt/sbt/releases/download/v0.13.15/sbt-0.13.15.tgz | tar -xzf - \
 && wget -q -c -O - http://ftp.fau.de/apache/maven/maven-3/3.5.0/binaries/apache-maven-3.5.0-bin.tar.gz | tar -xzf - \
 && wget -q -c -O - http://downloads.typesafe.com/zinc/0.3.13/zinc-0.3.13.tgz | tar -xzf - \
 && sed -i 's|session required pam_loginuid.so|session optional pam_loginuid.so|g' /etc/pam.d/sshd \
 && mkdir -p /var/run/sshd

RUN ln -s /opt/jdk1.8.0_131 /opt/java \
 && ln -s /opt/sbt-launcher-packaging-0.13.15 /opt/sbt \
 && ln -s /opt/apache-maven-3.5.0 /opt/maven \
 && ln -s /opt/zinc-0.3.13 /opt/zinc  \
 && echo "jenkins:jenkins" | chpasswd

USER ${USER}

RUN wget -q -c -O - https://raw.githubusercontent.com/creationix/nvm/v0.33.1/install.sh | /bin/bash; /bin/bash -l -c "source $HOME/.nvm/nvm.sh ; nvm install 4.2"

# Jenkins needs access to an open SSH port
EXPOSE 22

CMD ["/usr/sbin/sshd", "-D"]

