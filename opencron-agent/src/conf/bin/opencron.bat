@echo off
@REM
@REM Copyright (c) 2015 The Opencron Project
@REM
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements. See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership. The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License. You may obtain a copy of the License at
@REM
@REM http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied. See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM
@REM -----------------------------------------------------------------------------
@REM Control Script for the OPENCRON Server
@REM
@REM Environment Variable Prerequisites
@REM
@REM   OPENCRON_HOME   May point at your opencron "build" directory.
@REM
@REM   OPENCRON_BASE   (Optional) Base directory for resolving dynamic portions
@REM                   of a opencron installation.  If not present, resolves to
@REM                   the same directory that OPENCRON_HOME points to.
@REM
@REM   OPENCRON_OUT    (Optional) Full path to a file where stdout and stderr
@REM                   will be redirected.
@REM                   Default is $OPENCRON_BASE/logs/opencron.out
@REM
@REM   OPENCRON_TMPDIR (Optional) Directory path location of temporary directory
@REM                   the JVM should use (java.io.tmpdir).  Defaults to
@REM                   $OPENCRON_BASE/temp.
@REM -----------------------------------------------------------------------------

setlocal

@REM Suppress Terminate batch job on CTRL+C
if not ""%1"" == ""run"" goto mainEntry
if "%TEMP%" == "" goto mainEntry
if exist "%TEMP%\%~nx0.run" goto mainEntry
echo Y>"%TEMP%\%~nx0.run"
if not exist "%TEMP%\%~nx0.run" goto mainEntry
echo Y>"%TEMP%\%~nx0.Y"
call "%~f0" %* <"%TEMP%\%~nx0.Y"
@REM Use provided errorlevel
set RETVAL=%ERRORLEVEL%
del /Q "%TEMP%\%~nx0.Y" >NUL 2>&1
exit /B %RETVAL%
:mainEntry
del /Q "%TEMP%\%~nx0.run" >NUL 2>&1

@REM -----------------------------------------------------------------------------
@REM Guess OPENCRON_HOME if not defined
set OPENCRON_VERSION=1.2.0-RELEASE
set "WORK_DIR=%~dp0"
cd "%WORK_DIR%.."
set OPENCRON_HOME=%cd%
if not "%OPENCRON_BASE%" == "" goto gotBase
set "OPENCRON_BASE=%OPENCRON_HOME%"
set "OPENCRON_TMPDIR=%OPENCRON_BASE%\temp"
@REM -----------------------------------------------------------------------------

:gotBase
@REM Ensure that neither OPENCRON_HOME nor OPENCRON_BASE contains a semi-colon
@REM as this is used as the separator in the classpath and Java provides no
@REM mechanism for escaping if the same character appears in the path. Check this
@REM by replacing all occurrences of ';' with '' and checking that neither
@REM OPENCRON_HOME nor OPENCRON_BASE have changed
if "%OPENCRON_HOME%" == "%OPENCRON_HOME:;=%" goto homeNoSemicolon
echo Using OPENCRON_HOME:   "%OPENCRON_HOME%"
echo Unable to start as OPENCRON_HOME contains a semicolon (;) character
goto end

:homeNoSemicolon
if "%OPENCRON_BASE%" == "%OPENCRON_BASE:;=%" goto baseNoSemicolon
echo Using OPENCRON_BASE:   "%OPENCRON_BASE%"
echo Unable to start as OPENCRON_BASE contains a semicolon (;) character
goto end

:baseNoSemicolon
@REM Ensure that any user defined CLASSPATH variables are not used on startup,
@REM but allow them to be specified in setenv.bat, in rare case when it is needed.
set CLASSPATH=

@REM Get standard environment variables
if not exist "%OPENCRON_BASE%\bin\setenv.bat" goto checkSetenvHome
call "%OPENCRON_BASE%\bin\setenv.bat"
goto setenvDone
:checkSetenvHome
if exist "%OPENCRON_HOME%\bin\setenv.bat" call "%OPENCRON_HOME%\bin\setenv.bat"

:setenvDone
@REM Get standard Java environment variables
if exist "%OPENCRON_HOME%\bin\setclasspath.bat" goto okSetclasspath
echo Cannot find "%OPENCRON_HOME%\bin\setclasspath.bat"
echo This file is needed to run this program
goto end

:okSetclasspath
call "%OPENCRON_HOME%\bin\setclasspath.bat" %1
if errorlevel 1 goto end

@REM Add on extra jar file to CLASSPATH
@REM Note that there are no quotes as we do not want to introduce random
@REM quotes into the CLASSPATH

if "%CLASSPATH%" == "" goto emptyClasspath
set "CLASSPATH=%CLASSPATH%;"

:emptyClasspath
set "CLASSPATH=%CLASSPATH%%OPENCRON_HOME%\lib\opencron-agent-%OPENCRON_VERSION%.jar"


@REM ----- Execute The Requested Command ---------------------------------------
echo Using OPENCRON_BASE:   "%OPENCRON_BASE%"
echo Using OPENCRON_HOME:   "%OPENCRON_HOME%"
echo Using OPENCRON_TMPDIR: "%OPENCRON_TMPDIR%"

if "%TITLE%" == "" set TITLE=Opencron
set _EXECJAVA=start "%TITLE%" %_RUNJAVA%
set MAINCLASS=org.opencron.agent.AgentBootstrap

set Action=%1
if "%Action%" == "start" goto doAction
if "%Action%" == "stop" goto doAction
if "%Action%" == "version" goto doVersion

echo Usage:  opencron ( commands ... )
echo commands:
echo  start             Start Opencron-Agent
echo  stop              Stop Opencron-Agent
echo  version           print Opencron Version
goto  end

:doAction
%_EXECJAVA% ^
    -classpath "%CLASSPATH%" ^
    -Dopencron.home="%OPENCRON_HOME%" ^
    -Djava.io.tmpdir="%OPENCRON_TMPDIR%" ^
    %MAINCLASS% %Action%
goto end

:doVersion
echo %OPENCRON_VERSION%
goto end

:exit
exit /b 1

:end
exit /b 0