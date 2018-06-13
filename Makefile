.PHONY: all # Build all development project (not itest and docker)
all:
	mvn -Pbuild install

.PHONY: clean # Run mvn clean
clean:
	mvn clean

.PHONY: pom-xml # Generate pom.xml files
pom-xml:
	mvn -Pbuild,gen-pom-xml initialize

.PHOMY: eclipse # Generate Eclipse project files
eclipse: pom-xml
	mvn -Peclipse,build initialize de.tototec:de.tobiasroeser.eclipse-maven-plugin:0.1.1:eclipse

.PHONY: full # A full build including docker tests
full: docker-clean
	mvn -Pbuild,itest,docker clean install

.PHONY: light
light:
	mvn -Pbuild -DskipTests install

.PHONY: docker-clean # Cleanup old images from docker registry
docker-clean:
	for vm in $(docker ps -aq); do docker rm -f $vm; done
	for image in $(docker images | grep none | awk '{print $3;}'); do docker rmi -f $image; done

.PHONY: help # List of targets with descriptions
help:
	@grep '^.PHONY: .* #' Makefile | sed 's/\.PHONY: \(.*\) # \(.*\)/\1\t\2/' | expand -t20
