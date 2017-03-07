# The basic image to run Blended containers
FROM centos:centos6

MAINTAINER Andreas Gies version: 2.0-SNAPSHOT

# Installation section
RUN groupadd blended
RUN useradd -d /home/blended -s /bin/bash -g blended -m blended

ADD files/jdk-8u121-linux-x64.tar.gz /opt
ADD files/profile.d /etc/profile.d
RUN ln -s /opt/jdk1.8.0_121 /opt/java && yum update -y && yum install -y glibc.i686 

# End of Installation section
