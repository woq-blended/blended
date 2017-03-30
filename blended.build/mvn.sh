#!/bin/bash

/opt/maven/bin/mvn $@ | grep -v -i "download" | grep -v -i "CheckForNull" | grep -v -i "longer than 100 characters"

