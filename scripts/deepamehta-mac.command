#!/bin/sh
cd "$(dirname "$0")"
java -Djava.util.logging.config.file=conf/logging.properties -jar bin/felix.jar