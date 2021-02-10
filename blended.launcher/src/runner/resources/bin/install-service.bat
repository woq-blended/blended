
set SCRIPTPATH=%~dp0
set BLENDED_HOME=%SCRIPTPATH%..

set JAVA_HOME=%BLENDED_HOME%\jre
set JVM=%JAVA_HOME%\bin\client\jvm.dll

call %SCRIPTPATH%\setenv.bat

if "%SERVICE_NAME%"=="" (set SERVICE_NAME="BlendedDemo")

set SERVICE_ENV=BLENDED_HOME=%BLENDED_HOME%;JAVA_HOME=%JAVA_HOME%

if defined BLENDED_ENV set SERVICE_ENV=%SERVICE_ENV%;%BLENDED_ENV%

REM Restart delay in seconds, provide default if not set in setenv.bat
if not defined RESTART_DELAY set RESTART_DELAY=0

set CP=%BLENDED_HOME%/etc^
;%BLENDED_HOME%/lib/blended.launcher_@scala.binary.version@-@blended.launcher.version@.jar^
;%BLENDED_HOME%/lib/config-@typesafe.config.version@.jar^
;%BLENDED_HOME%/lib/org.osgi.core-@org.osgi.core.version@.jar^
;%BLENDED_HOME%/lib/blended.updater.config_@scala.binary.version@-@blended.updater.config.version@.jar^
;%BLENDED_HOME%/lib/blended.util_@scala.binary.version@-@blended.util.version@.jar^
;%BLENDED_HOME%/lib/blended.util.logging_@scala.binary.version@-@blended.util.logging.version@.jar^
;%BLENDED_HOME%/lib/de.tototec.cmdoption-@cmdoption.version@.jar^
;%BLENDED_HOME%/lib/scala-library-@scala.library.version@.jar^
;%BLENDED_HOME%/lib/slf4j-api-@slf4j.version@.jar^
;%BLENDED_HOME%/lib/logback-core-@logback.version@.jar^
;%BLENDED_HOME%/lib/logback-classic-@logback.version@.jar^
;%BLENDED_HOME%/lib/splunk-library-javalogging-@splunkjava.version@.jar^
;%BLENDED_HOME%/lib/httpcore-@httpcore.version@.jar^
;%BLENDED_HOME%/lib/httpcore-nio-@httpcorenio.version@.jar^
;%BLENDED_HOME%/lib/httpclient-@httpcomponents.version@.jar^
;%BLENDED_HOME%/lib/httpasyncclient-@httpasync.version@.jar^
;%BLENDED_HOME%/lib/commons-logging-@commonslogging.version@.jar^
;%BLENDED_HOME%/lib/json-simple-@jsonsimple.version@.jar

set LOGBACK_CONFIG_SETTING='-Dlogback.configurationFile=%BLENDED_HOME%/etc/logback.xml'

set CONTAINER_JAVA_OPTS='-jvmOpt=-Dsun.net.client.defaultConnectTimeout=500'
set CONTAINER_JAVA_OPTS=%CONTAINER_JAVA_OPTS%;'-jvmOpt=-Dsun.net.client.defaultReadTimeout=500'
set CONTAINER_JAVA_OPTS=%CONTAINER_JAVA_OPTS%;'-jvmOpt=%LOGBACK_CONFIG_SETTING%'
set CONTAINER_JAVA_OPTS=%CONTAINER_JAVA_OPTS%;'-jvmOpt=-Dblended.home=%BLENDED_HOME%'
set CONTAINER_JAVA_OPTS=%CONTAINER_JAVA_OPTS%;'-jvmOpt=-Djava.io.tmp=%BLENDED_HOME%/tmp'
REM set CONTAINER_JAVA_OPTS=%CONTAINER_JAVA_OPTS;'-jvmOpt=-Djavax.net.debug=ssl'

set CLASS=blended.launcher.jvmrunner.JvmLauncher

%SCRIPTPATH%prunsrv.exe ^
  //IS//%SERVICE_NAME% ^
  --DisplayName="%SERVICE_NAME%" ^
  --Environment=%SERVICE_ENV% ^
  --Jvm=%JVM% ^
  --Startup=auto ^
  --StartMode=jvm ^
  --StopMode=jvm ^
  --StartClass=%CLASS% ^
  --StartParams="start;-interactive=false;-maxShutdown=10;%CONTAINER_JAVA_OPTS%;-cp='%CP%';-restartDelay=%RESTART_DELAY%;--;blended.launcher.Launcher;--profile-lookup;%BLENDED_HOME%/launch.conf;--init-container-id;--framework-restart;false" ^
  --JvmOptions="%LOGBACK_CONFIG_SETTING%;-Dblended.home=%BLENDED_HOME%;-Xmx24m" ^
  --StopClass=%CLASS% ^
  --StopParams="stop" ^
  --Classpath="%CP%" ^
  --StdOutput=auto ^
  --StdError=auto ^
  --LogPath=%BLENDED_HOME%/log ^
  --LogLevel=Debug ^
  --LibraryPath=%JAVA_HOME%/bin
