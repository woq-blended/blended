# The basic image to run Blended containers
FROM centos:6.4

MAINTAINER Andreas Gies version: 1.0.7-M6-SNAPSHOT

# Installation section
RUN groupadd blended
RUN useradd -d /home/blended -s /bin/bash -g blended -m blended

ADD files/jdk-7u60-linux-x64.tar.gz /opt
RUN ln -s /opt/jdk1.7.0_60 /opt/java

# End of Installation section

