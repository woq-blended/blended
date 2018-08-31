.PHONY: clean
clean:
	mvn clean

.PHONY: build
build:
	mvn verify

.PHONY: run-client
run-client:
	mvn compile exec:exec -Dexec.executable="java" \
	-Dexec.workingdir="target/classes" \
	-Dexec.args="-cp %classpath blended.security.scep.standalone.ScepClientApp"
