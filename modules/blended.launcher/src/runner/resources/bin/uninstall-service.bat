set SCRIPTPATH=%~dp0

call %SCRIPTPATH%setenv.bat

if "%SERVICE_NAME%"=="" (set SERVICE_NAME="BlendedDemo")

%SCRIPTPATH%prunsrv.exe //DS//%SERVICE_NAME%
