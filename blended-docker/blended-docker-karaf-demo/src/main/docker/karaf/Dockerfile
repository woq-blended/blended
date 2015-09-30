# The basic image to run Blended containers
FROM atooni/blended-base:latest

MAINTAINER Andreas Gies version: ${project.version}

# Installation section
ADD ${docker.src.artifactId}-${project.version}-${docker.src.classifier}.${docker.src.type} /opt
RUN ln -s /opt/${docker.src.artifactId}-${project.version} /opt/${docker.target}
RUN mkdir -p /opt/${docker.target}/data
RUN chown -R blended.blended /opt/${docker.src.artifactId}-${project.version}
RUN chown -R blended.blended /opt/${docker.target}

USER blended
# End of Installation section

ENV JAVA_HOME /opt/java
ENV PATH ${PATH}:${JAVA_HOME}/bin

ENTRYPOINT ["/opt/${docker.target}/bin/karaf"]

EXPOSE 1883
EXPOSE 8181
EXPOSE 1099

