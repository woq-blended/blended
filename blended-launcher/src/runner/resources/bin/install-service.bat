
set SCRIPTPATH=%~dp0
set BLENDED_HOME=%SCRIPTPATH%..

set JAVA_HOME=%BLENDED_HOME%\jre
rem Das muss die Server VM sein!
rem Wenn client VM, dann fehlt eine Windows DLL msvcr71.dll
set JVM=%JAVA_HOME%\bin\server\jvm.dll

call %SCRIPTPATH%setenv.bat

set SERVICE_ENV=BLENDED_HOME=%BLENDED_HOME%;JAVA_HOME=%JAVA_HOME%

if defined BLENDED_ENV set SERVICE_ENV=%SERVICE_ENV%;%BLENDED_ENV%

set CP=%BLENDED_HOME%/etc^
;%BLENDED_HOME%/lib/blended.launcher-@blended.launcher.version@.jar^
;%BLENDED_HOME%/lib/config-@typesafe.config.version@.jar^
;%BLENDED_HOME%/lib/org.osgi.core-@org.osgi.core.version@.jar^
;%BLENDED_HOME%/lib/blended.updater.config-@blended.updater.config.version@.jar^
;%BLENDED_HOME%/lib/de.tototec.cmdoption-@cmdoption.version@.jar^
;%BLENDED_HOME%/lib/scala-library-@scala.library.version@.jar^
;%BLENDED_HOME%/lib/slf4j-api-@slf4j.version@.jar^
;%BLENDED_HOME%/lib/logback-core-@logback.version@.jar^
;%BLENDED_HOME%/lib/logback-classic-@logback.version@.jar

set CLASS=blended.launcher.jvmrunner.JvmLauncher

%SCRIPTPATH%prunsrv.exe ^
  //IS//BlendedDemo ^
  --DisplayName="Blended Demo (updateable)" ^
  --Environment=%SERVICE_ENV% ^
  --Jvm=%JVM% ^
  --StartMode=jvm ^
  --StopMode=jvm ^
  --StartClass=%CLASS% ^
  --StartParams="start;-jvmOpt=-Xmx512m;-cp='%CP%';--;blended.launcher.Launcher;--profile-lookup;%BLENDED_HOME%/launch.conf;--init-profile-props;--framework-restart;false" ^
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
