# The basic image to run Blended containers
FROM alpine:latest

MAINTAINER Andreas Gies 

# Installation section
ADD files/software/jdk-8u121-linux-x64.tar.gz /opt
ADD files/software/scala-2.11.8.tgz /opt
ADD files/software/sbt-0.13.13.tgz /opt
ADD files/software/apache-maven-3.3.9-bin.tar.gz /opt
ADD files/software/zinc-0.3.13.tgz /opt
ADD files/profile.d /etc/profile.d
ADD files/sudoer /etc/sudoers.d

RUN ln -s /opt/jdk1.8.0_121 /opt/java \
 && ln -s /opt/scala-2.11.8 /opt/scala \
 && ln -s /opt/sbt-launcher-packaging-0.13.13 /opt/sbt \
 && ln -s /opt/apache-maven-3.3.9 /opt/maven \
 && ln -s /opt/zinc-0.3.13 /opt/zinc \
 && groupadd blended ; useradd -d /home/blended -s /bin/bash -g blended -m blended \
 && apt-get -y update 
 
RUN apt-get install -y software-properties-common curl apt-transport-https net-tools systemd sudo 

RUN rm -f /bin/sh ; ln -s /bin/bash /bin/sh \
    && add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu xenial stable"

RUN curl -fsSL https://download.docker.com/linux/ubuntu/gpg | apt-key add -

RUN apt-get -y update

RUN apt-get -y install docker-ce
RUN usermod -a -G docker blended \
 && mkdir -p /var/lib/docker ; chown -R root:docker /var/lib/docker ; chmod -R g+w /var/lib/docker 

USER blended

RUN curl -o- https://raw.githubusercontent.com/creationix/nvm/v0.33.1/install.sh | bash
RUN /bin/bash -l -c "source $HOME/.nvm/nvm.sh ; nvm install 4.2"

ADD files/software/sbt-boot.tgz /home/blended/.sbt

# End of Installation section

ENTRYPOINT ["/bin/bash", "-l", "-c", "sh /home/blended/project/blended.build/docker-build.sh"]


