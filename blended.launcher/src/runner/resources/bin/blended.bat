set SCRIPTPATH=%~dp0
set BLENDED_HOME=%SCRIPTPATH%..
set JAVA_HOME=%BLENDED_HOME%\jre

%JAVA_HOME%\bin\java -version

call %SCRIPTPATH%\setenv.bat

set BLENDED_CP=%BLENDED_HOME%\etc
set BLENDED_CP=%BLENDED_CP%;%BLENDED_HOME%\lib\blended.launcher_@scala.binary.version@-@blended.launcher.version@.jar
set BLENDED_CP=%BLENDED_CP%;%BLENDED_HOME%\lib\config-@typesafe.config.version@.jar
set BLENDED_CP=%BLENDED_CP%;%BLENDED_HOME%\lib\org.osgi.core-@org.osgi.core.version@.jar
set BLENDED_CP=%BLENDED_CP%;%BLENDED_HOME%\lib\blended.updater.config_@scala.binary.version@-@blended.updater.config.version@.jar
set BLENDED_CP=%BLENDED_CP%;%BLENDED_HOME%\lib\blended.util_@scala.binary.version@-@blended.util.version@.jar
set BLENDED_CP=%BLENDED_CP%;%BLENDED_HOME%\lib\blended.util.logging_@scala.binary.version@-@blended.util.logging.version@.jar
set BLENDED_CP=%BLENDED_CP%;%BLENDED_HOME%\lib\de.tototec.cmdoption-@cmdoption.version@.jar
set BLENDED_CP=%BLENDED_CP%;%BLENDED_HOME%\lib\scala-library-@scala.library.version@.jar
set BLENDED_CP=%BLENDED_CP%;%BLENDED_HOME%\lib\slf4j-api-@slf4j.version@.jar
set BLENDED_CP=%BLENDED_CP%;%BLENDED_HOME%\lib\jcl-over-slf4j-@slf4j.version@.jar
set BLENDED_CP=%BLENDED_CP%;%BLENDED_HOME%\lib\log4j-@log4j.version@.jar
set BLENDED_CP=%BLENDED_CP%;%BLENDED_HOME%\lib\logback-core-@logback.version@.jar
set BLENDED_CP=%BLENDED_CP%;%BLENDED_HOME%\lib\logback-classic-@logback.version@.jar
set BLENDED_CP=%BLENDED_CP%;%BLENDED_HOME%\lib\splunk-library-javalogging-@splunkjava.version@.jar
set BLENDED_CP=%BLENDED_CP%;%BLENDED_HOME%\lib\httpcore-@httpcore.version@.jar
set BLENDED_CP=%BLENDED_CP%;%BLENDED_HOME%\lib\httpcore-nio-@httpcorenio.version@.jar
set BLENDED_CP=%BLENDED_CP%;%BLENDED_HOME%\lib\httpclient-@httpcomponents.version@.jar
set BLENDED_CP=%BLENDED_CP%;%BLENDED_HOME%\lib\httpasyncclient-@httpasync.version@.jar
set BLENDED_CP=%BLENDED_CP%;%BLENDED_HOME%\lib\commons-logging-@commonslogging.version@.jar
set BLENDED_CP=%BLENDED_CP%;%BLENDED_HOME%\lib\json-simple-@jsonsimple.version@.jar

%JAVA_HOME%\bin\java -cp %BLENDED_CP% -Dlogback.configurationFile=%BLENDED_HOME%\etc\logback.xml -Dblended.home=%BLENDED_HOME% -Dblended.laucher.startbundles=org.apache.felix.gogo.runtime,org.apache.felix.gogo.shell,org.apache.felix.gogo.command blended.launcher.Launcher --profile-lookup %BLENDED_HOME%\launch.conf --init-container-id --framework-restart false
