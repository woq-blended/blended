@echo off
setLocal EnableDelayedExpansion

set SERVICE_NAME=%1

set SCRIPT_HOME=%~dp0
cd %SCRIPT_HOME%\..

pushd
set KARAF_HOME=%cd%
set JAVA_HOME=%KARAF_HOME%\jre
popd

set CLASSPATH=

for %%f in (%SCRIPT_HOME%\*.jar) do (
  set CLASSPATH=!CLASSPATH!;%%f
)

%JAVA_HOME%\bin\java -cp %CLASSPATH% -DKARAF_JAVA_HOME=%JAVA_HOME% de.woq.osgi.java.installer.ServiceInstaller -b %KARAF_HOME% -n %SERVICE_NAME%