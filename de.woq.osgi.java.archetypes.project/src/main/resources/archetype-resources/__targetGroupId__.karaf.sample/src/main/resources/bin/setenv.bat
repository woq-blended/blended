REM
REM    Licensed to the Apache Software Foundation (ASF) under one or more
REM    contributor license agreements.  See the NOTICE file distributed with
REM    this work for additional information regarding copyright ownership.
REM    The ASF licenses this file to You under the Apache License, Version 2.0
REM    (the "License"); you may not use this file except in compliance with
REM    the License.  You may obtain a copy of the License at
REM
REM       http://www.apache.org/licenses/LICENSE-2.0
REM
REM    Unless required by applicable law or agreed to in writing, software
REM    distributed under the License is distributed on an "AS IS" BASIS,
REM    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
REM    See the License for the specific language governing permissions and
REM    limitations under the License.
REM

REM
REM handle specific scripts; the SCRIPT_NAME is exactly the name of the Karaf
REM script; for example karaf, start, stop, admin, client, ...
REM
REM if [ "$KARAF_SCRIPT" == "SCRIPT_NAME" ]; then
REM   Actions go here...
REM fi

REM
REM general settings which should be applied for all scripts go here; please keep
REM in mind that it is possible that scripts might be executed more than once, e.g.
REM in example of the start script where the start script is executed first and the
REM karaf script afterwards.
REM

REM
REM The following section shows the possible configuration options for the default
REM karaf scripts
REM

SET JAVA_MIN_MEM=128m
SET JAVA_MAX_MEM=256m

REM export JAVA_PERM_MEM REM Minimum perm memory for the JVM
REM export JAVA_MAX_PERM_MEM REM Maximum memory for the JVM
REM export KARAF_HOME REM Karaf home folder
REM export KARAF_DATA REM Karaf data folder
REM export KARAF_BASE REM Karaf base folder
REM export KARAF_OPTS REM Additional available Karaf options

@echo off
set OLD_DIR=%cd%
set SCRIPT_DIR=%~dp0%
set KARAF_DIR=%SCRIPT_DIR%..
cd %KARAF_DIR%
set KARAF_DIR=%cd%
set JAVA_HOME=%KARAF_DIR%\jre
set PATH=%PATH%;%JAVA_HOME%\bin;%KARAF_DIR%
cd /d %OLD_DIR%

@echo "OLD_DIR    :" %OLD_DIR%
@echo "SCRIPT_DIR :" %SCRIPT_DIR%
@echo "KARAF_DIR  :" %KARAF_DIR%
@echo "JAVA_HOME  :" %JAVA_HOME%
