# The basic image to run Blended containers
FROM centos:centos6

MAINTAINER Andreas Gies version: 2.0-SNAPSHOT

# Installation section
RUN groupadd blended
RUN useradd -d /home/blended -s /bin/bash -g blended -m blended

ADD files/jre-7u80-linux-x64.gz.tar.gz /opt
ADD files/profile /etc/profile.d
RUN ln -s /opt/jre1.7.0_80 /opt/java

# End of Installation section

