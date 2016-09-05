
set SCRIPTPATH=%~dp0
set BLENDED_HOME=%SCRIPTPATH%..

set BLENDED_VERSION=2.0-SNAPSHOT

set JAVA_HOME=%BLENDED_HOME%\jre
rem Das muss die Server VM sein!
rem Wenn client VM, dann fehlt eine Windows DLL msvcr71.dll
set JVM=%JAVA_HOME%\bin\server\jvm.dll

call %SCRIPTPATH%setenv.bat

set SERVICE_ENV=BLENDED_HOME=%BLENDED_HOME%;JAVA_HOME=%JAVA_HOME%

if defined BLENDED_ENV set SERVICE_ENV=%SERVICE_ENV%;%BLENDED_ENV%

# Restart delay in seconds, provide default if not set in setenv.bat
if not defined RESTART_DELAY set RESTART_DELAY=0

set CP=%BLENDED_HOME%/etc^
;%BLENDED_HOME%/lib/blended.launcher-%BLENDED_VERSION%.jar^
;%BLENDED_HOME%/lib/config-1.2.1.jar^
;%BLENDED_HOME%/lib/org.osgi.core-5.0.0.jar^
;%BLENDED_HOME%/lib/blended.updater.config-%BLENDED_VERSION%.jar^
;%BLENDED_HOME%/lib/de.tototec.cmdoption-0.4.2.jar^
;%BLENDED_HOME%/lib/scala-library-2.10.5.jar^
;%BLENDED_HOME%/lib/slf4j-api-1.7.12.jar^
;%BLENDED_HOME%/lib/logback-core-1.1.3.jar^
;%BLENDED_HOME%/lib/logback-classic-1.1.3.jar

set CLASS=blended.launcher.jvmrunner.JvmLauncher

%SCRIPTPATH%prunsrv.exe ^
  //IS//BlendedDemo ^
  --DisplayName="Blended Demo (updateable)" ^
  --Environment=%SERVICE_ENV% ^
  --Jvm=%JVM% ^
  --StartMode=jvm ^
  --StopMode=jvm ^
  --StartClass=%CLASS% ^
  --StartParams="start;-jvmOpt=-Xmx512m;-cp='%CP%';-restartDelay=%RESTART_DELAY%;--;blended.launcher.Launcher;--profile-lookup;%BLENDED_HOME%/launch.conf;--init-profile-props;--framework-restart;false" ^
  --JvmOptions="-Dlogback.configurationFile=%BLENDED_HOME%/etc/logback.xml" ^
  ++JvmOptions="-Dsun.net.client.defaultConnectTimeout=500" ^
  ++JvmOptions="-Dsun.net.client.defaultReadTimeout=500" ^
  ++JvmOptions="-Xmx24m" ^
  --StopClass=%CLASS% ^
  --StopParams="stop" ^
  --Classpath="%CP%" ^
  --StdOutput=auto ^
  --StdError=auto ^
  --LogPath=%BLENDED_HOME%/log ^
  --LogLevel=Debug ^
  --LibraryPath=%JAVA_HOME/bin
