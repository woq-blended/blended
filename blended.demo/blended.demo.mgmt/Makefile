.PHONY: run # Run the container
run:
	rlwrap sh target/blended.demo.*-*-SNAPSHOT/bin/blended.sh

.PHONY: show-log # Open the container logfile
show-log:
	less -i target/blended.demo.*-*-SNAPSHOT/log/blended.log

.PHONY: help # List of targets with descriptions
help:
	@grep '^.PHONY: .* #' Makefile | sed 's/\.PHONY: \(.*\) # \(.*\)/\1\t\2/' | expand -t20

