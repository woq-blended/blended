@echo off
setlocal enableDelayedExpansion

set SCRIPTDIR=%~dp0
cd %SCRIPTDIR%
cd .. 
set WOQ_HOME=%CD%

set JAVA_EXE=java

if exist "%WOQ_HOME%\jre\" (
 set JAVA="%WOQ_HOME%\jre\bin\%JAVA_EXE%"
 ) ELSE if NOT "%JAVA_HOME%" == "" (
 set JAVA="%JAVA_HOME%\bin\%JAVA_EXE%"
 ) ELSE (set JAVA="%JAVA_EXE%"
 )

for /F %%x in ('dir /B/D %WOQ_HOME%\lib') do (
  call %WOQ_HOME%\bin\appendcp.cmd %WOQ_HOME%\lib\%%x
)
call %WOQ_HOME%\bin\appendcp.cmd %WOQ_HOME%\config

echo WOQ container directory is [%WOQ_HOME%]

%JAVA_EXE% -version 
%JAVA% -classpath %APPCP% de.woq.osgi.java.container.WOQContainer -jvm.property.woq.home %WOQ_HOME% %1
