@echo off
setLocal EnableDelayedExpansion

set JAVA_HOME=C:\Program Files\Java\jdk1.6.0_45

echo %JAVA_HOME%

pushd ..
set APP_HOME=%cd%
popd
echo %APP_HOME%

set CLASSPATH=%APP_HOME%\ruby;%APP_HOME%\config;%APP_HOME%\features

for %%f in (%APP_HOME%\lib\*.jar) do (
  set CLASSPATH=!CLASSPATH!;%%f
)

set CP=%APP_HOME%/ruby-runtime/ruby-core

for /f "delims=" %%A in ('forfiles /P "%APP_HOME%\ruby-runtime\ruby-gems" /s /m shared /c "cmd /c echo @relpath"') do (
  set "file=%%~A"
  set CP=!CP!;%APP_HOME%\ruby-runtime\ruby-gems\!file:~2!
)

for /f "delims=" %%A in ('forfiles /P "%APP_HOME%\ruby-runtime\ruby-gems" /s /m lib /c "cmd /c echo @relpath"') do (
  set "file=%%~A"
  set CP=!CP!;%APP_HOME%\ruby-runtime\ruby-gems\!file:~2!
)

set CP=%CP:\=/%

"%JAVA_HOME%\bin\java" -Dsonicsw.home="%APP_HOME%\schemas" -Dgem.path="%APP_HOME%\ruby-runtime\ruby-gems" -Dorg.jruby.embed.class.path="%CP%"  -Dlog4j.configuration="%APP_HOME%/config/log4j.properties" com.ba.sip.inf.tools.jruby.JRubyRunner %* -app.home %APP_HOME% --app.config %APP_HOME%/config