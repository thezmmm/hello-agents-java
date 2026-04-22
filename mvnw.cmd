@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements. See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership. The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License. You may obtain a copy of the License at
@REM
@REM     https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied. See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM Begin all REM lines with '@' in case MAVEN_BATCH_ECHO is 'on'
@echo off
@REM set title of command window
title %0
@REM enable echoing by setting MAVEN_BATCH_ECHO to 'on'
@if "%MAVEN_BATCH_ECHO%" == "on"  echo %MAVEN_BATCH_ECHO%

@REM set %HOME% to equivalent of $HOME
if "%HOME%" == "" (set "HOME=%HOMEDRIVE%%HOMEPATH%")

@REM Execute a user defined script before this one
if not "%MAVEN_SKIP_RC%"=="" goto skipRcPre
@REM check for pre script, once with legacy .bat ending and once with .cmd ending
if exist "%USERPROFILE%\mavenrc_pre.bat" call "%USERPROFILE%\mavenrc_pre.bat" %*
if exist "%USERPROFILE%\mavenrc_pre.cmd" call "%USERPROFILE%\mavenrc_pre.cmd" %*
:skipRcPre

@setlocal

set ERROR_CODE=0

@REM To isolate internal variables from target environment also use local variables
set MAVEN_PROJECTBASEDIR=%~dp0

:find_wrapper
if exist "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" goto init
SET MAVEN_WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"

@REM Download the maven-wrapper.jar if missing
if not exist %MAVEN_WRAPPER_JAR% (
  echo Downloading maven-wrapper.jar from Maven repository...
  powershell -Command "&{[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar' -OutFile '%MAVEN_WRAPPER_JAR%'}"
)

:init
set MAVEN_OPTS_CMD_LINE_ARGS=

@REM Find the project base dir, i.e. the directory that contains the folder ".mvn".
@REM Fallback to current directory if not found.

set EXEC_DIR=%CD%
set WDIR=%EXEC_DIR%

@REM Detect if we are running as a user with Administrative privileges or not.
set WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

set WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
set WRAPPER_LAUNCHER_CLASSPATH=

@REM Find JAVA_HOME
if defined JAVA_HOME goto init_maven

for %%i in (java.exe) do set JAVA_EXE=%%~$PATH:i
if defined JAVA_EXE goto init_maven

echo.
echo ERROR: JAVA_HOME not found in your environment. >&2
echo Please set the JAVA_HOME variable in your environment to match the >&2
echo location of your Java installation. >&2
echo.
goto error

:init_maven
if not defined JAVA_HOME (
  set JAVA_HOME=E:\Java\jdk-17.0.1
)
set JAVA_EXE="%JAVA_HOME%\bin\java.exe"

:exec_maven
%JAVA_EXE% %JVM_CONFIG_MAVEN_PROPS% %MAVEN_OPTS% %MAVEN_DEBUG_OPTS% -classpath %WRAPPER_JAR% "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" %WRAPPER_LAUNCHER% %MAVEN_CONFIG% %*
if ERRORLEVEL 1 goto error
goto end

:error
set ERROR_CODE=1

:end
@endlocal & set ERROR_CODE=%ERROR_CODE%
if not "%MAVEN_SKIP_RC%"=="" goto skipRcPost
@REM check for post script, once with legacy .bat ending and once with .cmd ending
if exist "%USERPROFILE%\mavenrc_post.bat" call "%USERPROFILE%\mavenrc_post.bat"
if exist "%USERPROFILE%\mavenrc_post.cmd" call "%USERPROFILE%\mavenrc_post.cmd"
:skipRcPost

@REM Pause the script if MAVEN_BATCH_PAUSE is set to 'on'
if "%MAVEN_BATCH_PAUSE%"=="on" pause

exit /B %ERROR_CODE%
