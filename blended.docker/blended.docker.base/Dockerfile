# The basic image to run Blended containers
FROM centos:centos6

MAINTAINER Andreas Gies version: 2.0-SNAPSHOT

# Installation section
RUN groupadd blended
RUN useradd -d /home/blended -s /bin/bash -g blended -m blended

ADD files/jdk-8u111-linux-x64.tar.gz /opt
ADD files/profile /etc/profile.d
RUN ln -s /opt/jdk1.8.0_111 /opt/java

# End of Installation section
