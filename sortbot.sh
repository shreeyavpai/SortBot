#!/bin/sh
# Runs the SortBot command-line interface. Build first with: mvn -q compile
exec java -cp "$(dirname "$0")/target/classes" com.sortbot.ui.cli.Main "$@"
