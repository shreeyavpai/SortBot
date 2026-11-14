@echo off
rem Starts the SortBot window. Build first with: mvn -q compile
java -cp "%~dp0target\classes" com.sortbot.ui.gui.GuiMain %*
