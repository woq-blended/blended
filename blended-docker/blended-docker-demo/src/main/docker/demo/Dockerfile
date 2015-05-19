# The basic image to run Blended containers
FROM atooni/blended-base:latest

MAINTAINER Andreas Gies version: ${project.version}

# Installation section
ADD blended.karaf.demo-${project.version}-nojre.tar.gz /opt
RUN ln -s /opt/blended.karaf.demo-${project.version} /opt/blended
RUN mkdir -p /opt/blended/data
RUN chown -R blended.blended /opt/blended*

USER blended
# End of Installation section

ENV JAVA_HOME /opt/java
ENV PATH ${PATH}:${JAVA_HOME}/bin

ENTRYPOINT ["/opt/blended/bin/karaf"]

EXPOSE 1883
EXPOSE 8181
EXPOSE 1099

