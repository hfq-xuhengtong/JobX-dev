@echo off
@REM Copyright (c) 2015 The Opencron Project
@REM <p>
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements. See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership. The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License. You may obtain a copy of the License at
@REM <p>
@REM http://www.apache.org/licenses/LICENSE-2.0
@REM <p>
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied. See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM

setlocal

rem Suppress Terminate batch job on CTRL+C
if not ""%1"" == ""run"" goto mainEntry
if "%TEMP%" == "" goto mainEntry
if exist "%TEMP%\%~nx0.run" goto mainEntry
echo Y>"%TEMP%\%~nx0.run"
if not exist "%TEMP%\%~nx0.run" goto mainEntry
echo Y>"%TEMP%\%~nx0.Y"
call "%~f0" %* <"%TEMP%\%~nx0.Y"
rem Use provided errorlevel
set RETVAL=%ERRORLEVEL%
del /Q "%TEMP%\%~nx0.Y" >NUL 2>&1
exit /B %RETVAL%
:mainEntry
del /Q "%TEMP%\%~nx0.run" >NUL 2>&1

rem Guess OPENCRON_HOME if not defined
set "CURRENT_DIR=%cd%"
if not "%OPENCRON_HOME%" == "" goto gotHome
set "OPENCRON_HOME=%CURRENT_DIR%"
if exist "%OPENCRON_HOME%\bin\OPENCRON.bat" goto okHome
cd ..
set "OPENCRON_HOME=%cd%"
cd "%CURRENT_DIR%"
:gotHome

if exist "%OPENCRON_HOME%\bin\OPENCRON.bat" goto okHome
echo The OPENCRON_HOME environment variable is not defined correctly
echo This environment variable is needed to run this program
goto end
:okHome

rem Copy OPENCRON_BASE from OPENCRON_HOME if not defined
if not "%OPENCRON_BASE%" == "" goto gotBase
set "OPENCRON_BASE=%OPENCRON_HOME%"
:gotBase

rem Ensure that neither OPENCRON_HOME nor OPENCRON_BASE contains a semi-colon
rem as this is used as the separator in the classpath and Java provides no
rem mechanism for escaping if the same character appears in the path. Check this
rem by replacing all occurrences of ';' with '' and checking that neither
rem OPENCRON_HOME nor OPENCRON_BASE have changed
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

rem Ensure that any user defined CLASSPATH variables are not used on startup,
rem but allow them to be specified in setenv.bat, in rare case when it is needed.
set CLASSPATH=

rem Get standard environment variables
if not exist "%OPENCRON_BASE%\bin\setenv.bat" goto checkSetenvHome
call "%OPENCRON_BASE%\bin\setenv.bat"
goto setenvDone
:checkSetenvHome
if exist "%OPENCRON_HOME%\bin\setenv.bat" call "%OPENCRON_HOME%\bin\setenv.bat"
:setenvDone

rem Get standard Java environment variables
if exist "%OPENCRON_HOME%\bin\setclasspath.bat" goto okSetclasspath
echo Cannot find "%OPENCRON_HOME%\bin\setclasspath.bat"
echo This file is needed to run this program
goto end
:okSetclasspath
call "%OPENCRON_HOME%\bin\setclasspath.bat" %1
if errorlevel 1 goto end

rem Add on extra jar file to CLASSPATH
rem Note that there are no quotes as we do not want to introduce random
rem quotes into the CLASSPATH
if "%CLASSPATH%" == "" goto emptyClasspath
set "CLASSPATH=%CLASSPATH%;"
:emptyClasspath
set "CLASSPATH=%CLASSPATH%%OPENCRON_HOME%\bin\bootstrap.jar"

if not "%OPENCRON_TMPDIR%" == "" goto gotTmpdir
set "OPENCRON_TMPDIR=%OPENCRON_BASE%\temp"
:gotTmpdir

rem Add tomcat-juli.jar to classpath
rem tomcat-juli.jar can be over-ridden per instance
if not exist "%OPENCRON_BASE%\bin\tomcat-juli.jar" goto juliClasspathHome
set "CLASSPATH=%CLASSPATH%;%OPENCRON_BASE%\bin\tomcat-juli.jar"
goto juliClasspathDone
:juliClasspathHome
set "CLASSPATH=%CLASSPATH%;%OPENCRON_HOME%\bin\tomcat-juli.jar"
:juliClasspathDone

if not "%JSSE_OPTS%" == "" goto gotJsseOpts
set JSSE_OPTS="-Djdk.tls.ephemeralDHKeySize=2048"
:gotJsseOpts
set "JAVA_OPTS=%JAVA_OPTS% %JSSE_OPTS%"

rem Register custom URL handlers
rem Do this here so custom URL handles (specifically 'war:...') can be used in the security policy
set "JAVA_OPTS=%JAVA_OPTS% -Djava.protocol.handler.pkgs=org.apache.OPENCRON.webresources"

if not "%LOGGING_CONFIG%" == "" goto noJuliConfig
set LOGGING_CONFIG=-Dnop
if not exist "%OPENCRON_BASE%\conf\logging.properties" goto noJuliConfig
set LOGGING_CONFIG=-Djava.util.logging.config.file="%OPENCRON_BASE%\conf\logging.properties"
:noJuliConfig

if not "%LOGGING_MANAGER%" == "" goto noJuliManager
set LOGGING_MANAGER=-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager
:noJuliManager

rem ----- Execute The Requested Command ---------------------------------------

echo Using OPENCRON_BASE:   "%OPENCRON_BASE%"
echo Using OPENCRON_HOME:   "%OPENCRON_HOME%"
echo Using OPENCRON_TMPDIR: "%OPENCRON_TMPDIR%"
if ""%1"" == ""debug"" goto use_jdk
echo Using JRE_HOME:        "%JRE_HOME%"
goto java_dir_displayed
:use_jdk
echo Using JAVA_HOME:       "%JAVA_HOME%"
:java_dir_displayed
echo Using CLASSPATH:       "%CLASSPATH%"

set _EXECJAVA=%_RUNJAVA%
set MAINCLASS=org.apache.OPENCRON.startup.Bootstrap
set ACTION=start
set SECURITY_POLICY_FILE=
set DEBUG_OPTS=
set JPDA=

if not ""%1"" == ""jpda"" goto noJpda
set JPDA=jpda
if not "%JPDA_TRANSPORT%" == "" goto gotJpdaTransport
set JPDA_TRANSPORT=dt_socket
:gotJpdaTransport
if not "%JPDA_ADDRESS%" == "" goto gotJpdaAddress
set JPDA_ADDRESS=localhost:8000
:gotJpdaAddress
if not "%JPDA_SUSPEND%" == "" goto gotJpdaSuspend
set JPDA_SUSPEND=n
:gotJpdaSuspend
if not "%JPDA_OPTS%" == "" goto gotJpdaOpts
set JPDA_OPTS=-agentlib:jdwp=transport=%JPDA_TRANSPORT%,address=%JPDA_ADDRESS%,server=y,suspend=%JPDA_SUSPEND%
:gotJpdaOpts
shift
:noJpda

if ""%1"" == ""debug"" goto doDebug
if ""%1"" == ""run"" goto doRun
if ""%1"" == ""start"" goto doStart
if ""%1"" == ""stop"" goto doStop
if ""%1"" == ""configtest"" goto doConfigTest
if ""%1"" == ""version"" goto doVersion

echo Usage:  OPENCRON ( commands ... )
echo commands:
echo   debug             Start OPENCRON in a debugger
echo   debug -security   Debug OPENCRON with a security manager
echo   jpda start        Start OPENCRON under JPDA debugger
echo   run               Start OPENCRON in the current window
echo   run -security     Start in the current window with security manager
echo   start             Start OPENCRON in a separate window
echo   start -security   Start in a separate window with security manager
echo   stop              Stop OPENCRON
echo   configtest        Run a basic syntax check on server.xml
echo   version           What version of tomcat are you running?
goto end

:doDebug
shift
set _EXECJAVA=%_RUNJDB%
set DEBUG_OPTS=-sourcepath "%OPENCRON_HOME%\..\..\java"
if not ""%1"" == ""-security"" goto execCmd
shift
echo Using Security Manager
set "SECURITY_POLICY_FILE=%OPENCRON_BASE%\conf\OPENCRON.policy"
goto execCmd

:doRun
shift
if not ""%1"" == ""-security"" goto execCmd
shift
echo Using Security Manager
set "SECURITY_POLICY_FILE=%OPENCRON_BASE%\conf\OPENCRON.policy"
goto execCmd

:doStart
shift
if "%TITLE%" == "" set TITLE=Tomcat
set _EXECJAVA=start "%TITLE%" %_RUNJAVA%
if not ""%1"" == ""-security"" goto execCmd
shift
echo Using Security Manager
set "SECURITY_POLICY_FILE=%OPENCRON_BASE%\conf\OPENCRON.policy"
goto execCmd

:doStop
shift
set ACTION=stop
set OPENCRON_OPTS=
goto execCmd

:doConfigTest
shift
set ACTION=configtest
set OPENCRON_OPTS=
goto execCmd

:doVersion
%_EXECJAVA% -classpath "%OPENCRON_HOME%\lib\OPENCRON.jar" org.apache.OPENCRON.util.ServerInfo
goto end


:execCmd
rem Get remaining unshifted command line arguments and save them in the
set CMD_LINE_ARGS=
:setArgs
if ""%1""=="""" goto doneSetArgs
set CMD_LINE_ARGS=%CMD_LINE_ARGS% %1
shift
goto setArgs
:doneSetArgs

rem Execute Java with the applicable properties
if not "%JPDA%" == "" goto doJpda
if not "%SECURITY_POLICY_FILE%" == "" goto doSecurity
%_EXECJAVA% %LOGGING_CONFIG% %LOGGING_MANAGER% %JAVA_OPTS% %OPENCRON_OPTS% %DEBUG_OPTS% -classpath "%CLASSPATH%" -DOPENCRON.base="%OPENCRON_BASE%" -DOPENCRON.home="%OPENCRON_HOME%" -Djava.io.tmpdir="%OPENCRON_TMPDIR%" %MAINCLASS% %CMD_LINE_ARGS% %ACTION%
goto end
:doSecurity
%_EXECJAVA% %LOGGING_CONFIG% %LOGGING_MANAGER% %JAVA_OPTS% %OPENCRON_OPTS% %DEBUG_OPTS% -classpath "%CLASSPATH%" -Djava.security.manager -Djava.security.policy=="%SECURITY_POLICY_FILE%" -DOPENCRON.base="%OPENCRON_BASE%" -DOPENCRON.home="%OPENCRON_HOME%" -Djava.io.tmpdir="%OPENCRON_TMPDIR%" %MAINCLASS% %CMD_LINE_ARGS% %ACTION%
goto end
:doJpda
if not "%SECURITY_POLICY_FILE%" == "" goto doSecurityJpda
%_EXECJAVA% %LOGGING_CONFIG% %LOGGING_MANAGER% %JAVA_OPTS% %JPDA_OPTS% %OPENCRON_OPTS% %DEBUG_OPTS% -classpath "%CLASSPATH%" -DOPENCRON.base="%OPENCRON_BASE%" -DOPENCRON.home="%OPENCRON_HOME%" -Djava.io.tmpdir="%OPENCRON_TMPDIR%" %MAINCLASS% %CMD_LINE_ARGS% %ACTION%
goto end
:doSecurityJpda
%_EXECJAVA% %LOGGING_CONFIG% %LOGGING_MANAGER% %JAVA_OPTS% %JPDA_OPTS% %OPENCRON_OPTS% %DEBUG_OPTS% -classpath "%CLASSPATH%" -Djava.security.manager -Djava.security.policy=="%SECURITY_POLICY_FILE%" -DOPENCRON.base="%OPENCRON_BASE%" -DOPENCRON.home="%OPENCRON_HOME%" -Djava.io.tmpdir="%OPENCRON_TMPDIR%" %MAINCLASS% %CMD_LINE_ARGS% %ACTION%
goto end

:end
