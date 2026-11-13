@echo off
rem Runs the SortBot command-line interface. Build first with: mvn -q compile
java -cp "%~dp0target\classes" com.sortbot.ui.cli.Main %*
