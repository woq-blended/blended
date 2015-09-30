# The basic image to run Blended containers
FROM atooni/blended-base:latest

MAINTAINER Andreas Gies version: ${project.version}

# Installation section
ADD blended.launcher.demo-${project.version}-full-nojre.tar.gz /opt
RUN ln -s /opt/blended.launcher.demo-${project.version} /opt/blended
RUN chown -R blended.blended /opt/blended*

USER blended
# End of Installation section

ENV JAVA_HOME /opt/java
ENV PATH ${PATH}:${JAVA_HOME}/bin

ENTRYPOINT ["/bin/bash"]

EXPOSE 1883
EXPOSE 9191
EXPOSE 1099

