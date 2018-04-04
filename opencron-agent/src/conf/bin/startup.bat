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

@REM ---------------------------------------------------------------------------
@REM Start script for the OPENCRON agent
@REM ---------------------------------------------------------------------------

setlocal

@REM Guess OPENCRON_HOME if not defined
set WORK_DIR=%~dp0
cd "%WORK_DIR%.."
set OPENCRON_HOME=%cd%
set EXECUTABLE=%OPENCRON_HOME%\bin\opencron.bat

if exist "%EXECUTABLE%" goto okExec
echo Cannot find "%EXECUTABLE%"
echo This file is needed to run this program
goto exit

:okExec
@REM Get remaining unshifted command line arguments and save them in the
set CMD_LINE_ARGS=
:setArgs
if ""%1""=="""" goto doneSetArgs
set CMD_LINE_ARGS=%CMD_LINE_ARGS% %1
shift
goto setArgs
:doneSetArgs

call "%EXECUTABLE%" start %CMD_LINE_ARGS%

:end

:exit
exit /b 1

:end
exit /b 0