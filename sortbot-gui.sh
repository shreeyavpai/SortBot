#!/bin/sh
# Starts the SortBot window. Build first with: mvn -q compile
exec java -cp "$(dirname "$0")/target/classes" com.sortbot.ui.gui.GuiMain "$@"
