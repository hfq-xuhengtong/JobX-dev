@echo off
@REM Licensed to the Apache Software Foundation (ASF) under one or more
@REM contributor license agreements.  See the NOTICE file distributed with
@REM this work for additional information regarding copyright ownership.
@REM The ASF licenses this file to You under the Apache License, Version 2.0
@REM (the "License"); you may not use this file except in compliance with
@REM the License.  You may obtain a copy of the License at
@REM
@REM     http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing, software
@REM distributed under the License is distributed on an "AS IS" BASIS,
@REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM See the License for the specific language governing permissions and
@REM limitations under the License.

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

@REM -----------------------------------------------------------------------------
set OPENCRON_VERSION=1.2.0-RELEASE

@REM -----------------------------------------------------------------------------

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

@REM Guess OPENCRON_HOME if not defined
set "CURRENT_DIR=%cd%"
if not "%OPENCRON_HOME%" == "" goto gotHome
set "OPENCRON_HOME=%CURRENT_DIR%"
if exist "%OPENCRON_HOME%\bin\opencron.bat" goto okHome
cd ..
set "OPENCRON_HOME=%cd%"
cd "%CURRENT_DIR%"
:gotHome

if exist "%OPENCRON_HOME%\bin\opencron.bat" goto okHome
echo The OPENCRON_HOME environment variable is not defined correctly
echo This environment variable is needed to run this program
goto end
:okHome

@REM Copy OPENCRON_BASE from OPENCRON_HOME if not defined
if not "%OPENCRON_BASE%" == "" goto gotBase
set "OPENCRON_BASE=%OPENCRON_HOME%"
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

if not "%OPENCRON_TMPDIR%" == "" goto gotTmpdir
set "OPENCRON_TMPDIR=%OPENCRON_BASE%\temp"
:gotTmpdir

if not "%OPENCRON_PORT%" == "" goto defPort
set "OPENCRON_PORT=1577"
:defPort

if not exist  "%OPENCRON_BASE%\.password" goto defPassword
set "OPENCRON_PASSWORD="opencron"
echo opencron password not input,will be used password:opencron"
:defPassword

if exist "%OPENCRON_BASE%\.password" goto readPassword
for /f "delims=" %%p in ("%OPENCRON_BASE%\.password") do (
    if "%%p" == "" goto defPassword
    set "OPENCRON_PASSWORD=%%p"
    goto :out
)
:readPassword
:out

if exist "%OPENCRON_OUT%" goto setOut
set OPENCRON_OUT=%OPENCRON_BASE%\logs\opencron.out
:setOut

@REM ----- Execute The Requested Command ---------------------------------------
echo Using OPENCRON_BASE:   "%OPENCRON_BASE%"
echo Using OPENCRON_HOME:   "%OPENCRON_HOME%"
echo Using OPENCRON_TMPDIR: "%OPENCRON_TMPDIR%"
echo Using OPENCRON_PORT:   "%OPENCRON_PORT%"

set _EXECJAVA=%_RUNJAVA%
set MAINCLASS=org.opencron.agent.AgentBootstrap
set ACTION=start

if ""%1"" == ""start"" goto doStart
if ""%1"" == ""stop"" goto doStop

echo Usage:  OPENCRON ( commands ... )
echo commands:
echo   start             Start OPENCRON in a separate window
echo   stop              Stop OPENCRON
goto end

:doStart
shift
if "%TITLE%" == "" set TITLE=Opencron
set _EXECJAVA=start "%TITLE%" %_RUNJAVA%
goto execCmd

:doStop
shift
set ACTION=stop
goto execCmd

:execCmd
set CMD_LINE_ARGS=
:setArgs
if ""%1""=="""" goto doneSetArgs
set CMD_LINE_ARGS=%CMD_LINE_ARGS% %1
shift
goto setArgs
:doneSetArgs

%_EXECJAVA% ^
    -classpath "%CLASSPATH%" ^
    -Dopencron.home="%OPENCRON_HOME%" ^
    -Djava.io.tmpdir="%OPENCRON_TMPDIR%" ^
    -Dopencron.port="%OPENCRON_PORT%" ^
    -Dopencron.host="%OPENCRON_HOST%" ^
    -Dopencron.password="%OPENCRON_PASSWORD%" ^
    %MAINCLASS% %ACTION%

goto end

:exit
exit /b 1

:end
exit /b 0