# The basic image to run Blended containers
FROM atooni/blended-base:latest

MAINTAINER Andreas Gies version: ${project.version}

# Installation section
ADD ${docker.src.artifactId}-${docker.src.version}-${docker.src.classifier}.${docker.src.type} /opt
RUN ln -s /opt/blended.launcher.demo-${project.version} /opt/${docker.target}
RUN chown -R blended.blended /opt/${docker.src.artifactId}-${project.version}
RUN chown -R blended.blended /opt/${docker.target}

USER blended
# End of Installation section

ENV JAVA_HOME /opt/java
ENV PATH ${PATH}:${JAVA_HOME}/bin

ENTRYPOINT ["/bin/sh", "/opt/${docker.target}/bin/blended.sh"]

EXPOSE 1883
EXPOSE 8181
EXPOSE 1099

