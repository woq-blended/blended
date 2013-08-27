set CURRENTDIR=%~dp0
set WOQ_HOME=%CURRENTDIR:~0,-1%

set JAVA_EXE=javaw

if exist "%WOQ_HOME%\jre\" (
 set JAVA="%WOQ_HOME%\jre\bin\%JAVA_EXE%"
 ) ELSE if NOT "%JAVA_HOME%" == "" (
 set JAVA="%JAVA_HOME%\bin\%JAVA_EXE%"
 ) ELSE (set JAVA="%JAVA_EXE%"
 )

set WHITEBOARD_JARS=%WHITEBOARD_HOME%\system\de\yconx\whiteboard\de.yconx.whiteboard.launcher\${de.yconx.whiteboard.launcher.version}\de.yconx.whiteboard.launcher-${de.yconx.whiteboard.launcher.version}.jar
set WHITEBOARD_JARS="%WHITEBOARD_JARS%;%WHITEBOARD_HOME%\system\de\yconx\whiteboard\de.yconx.whiteboard.core.feature\${de.yconx.whiteboard.core.feature.version}\de.yconx.whiteboard.core.feature-${de.yconx.whiteboard.core.feature.version}.jar"

set WB_CLASSPATH=%EQUINOX_JAR%;%WHITEBOARD_JARS%;%LAF_JAR%
set WB_FEATURE=net/fireboard/net.fireboard.spi.featurelauncher/${net.fireboard.spi.featurelauncher.version}/net.fireboard.spi.featurelauncher-${net.fireboard.spi.featurelauncher.version}.jar
set WB_THEMES=net/fireboard/net.fireboard.ui.spi.themes/${net.fireboard.ui.spi.themes.version}/net.fireboard.ui.spi.themes-${net.fireboard.ui.spi.themes.version}.jar

start "WOQContainer" /D"%WOQ_HOME%" %JAVA%  -Dwhiteboard.home="%WHITEBOARD_HOME%" -classpath %WB_CLASSPATH% de.yconx.whiteboard.launcher.Whiteboard