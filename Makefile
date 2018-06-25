.PHONY: help # List of targets with descriptions
help:
	@grep '^\.PHONY: .* #' Makefile | sed 's/\.PHONY: \(.*\) # \(.*\)/\1\t\2/' | expand -t20

.PHONY: build # Build all development project (not itest and docker)
build:
	mvn -Pbuild install

.PHONY: clean # Run mvn clean
clean:
	mvn -Pbuild,itest,docker --fail-at-end clean

.PHONY: pom-xml # Generate pom.xml files
pom-xml:
	mvn -Pbuild,gen-pom-xml initialize

.PHOMY: eclipse # Generate Eclipse project files
eclipse: pom-xml
	mvn -Peclipse,build initialize de.tototec:de.tobiasroeser.eclipse-maven-plugin:0.1.1:eclipse

.PHONY: full # A full build including docker tests
full: docker-clean
	mvn -Pbuild,itest,docker install

.PHONY: light # Build but skip test executions
light:
	mvn -Pbuild -DskipTests install

.PHONY: docker-clean # Cleanup old images from docker registry
docker-clean:
	for vm in $$(docker ps -aq); do \
		docker rm -f $$vm; \
	done
	for image in $$(docker images | grep none | awk '{print $$3;}'); \
		do docker rmi -f $$image; \
	done

.PHONY: travis-prepare # Prepare travis env, e.g. pre-fetching maven (somewhat quieter)
travis-prepare:
	# Errors in the next command are ignored
	-mvn --fail-at-end dependency:resolve -Dsilent=true | grep -vi download

.PHONY: travis-build # Build the project with travis
travis-build: build

.PHONY: travis # Run a full travis build
travis: travis-prepare travis-build
