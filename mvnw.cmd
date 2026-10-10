@REM
@REM KnowIt - minimal Maven Wrapper for Windows.
@REM
@REM IMPORTANT: this file MUST stay ASCII-only.
@REM   cmd.exe reads .bat/.cmd files using the OEM code page (936/GBK on a
@REM   Chinese-locale Windows). A UTF-8 file containing non-ASCII characters is
@REM   mis-decoded, which can corrupt quoting and break the script entirely.
@REM   Observed failure mode: Chinese echo text turned into executed commands
@REM   such as "'mvn' is not recognized".
@REM   If you need Chinese text here, do not - put it in a UTF-8 readme instead.
@REM
@REM Behaviour:
@REM   1. Apply .mvn/jvm.config (one JVM argument per line) to MAVEN_OPTS.
@REM   2. Delegate to 'mvn' if it is on PATH.
@REM   3. Otherwise print instructions. The sh wrapper (./mvnw) downloads Maven
@REM      automatically and is the primary entry point on Linux/macOS/CI.
@REM

@echo off
setlocal enabledelayedexpansion

set "MAVEN_PROJECTBASEDIR=%~dp0"
if "%MAVEN_PROJECTBASEDIR:~-1%"=="\" set "MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%"

if not exist "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties" (
  echo ERROR: missing .mvn\wrapper\maven-wrapper.properties
  exit /b 1
)

REM Apply .mvn/jvm.config. Note: that file supports one arg per line and does
REM NOT support comments - '#' would be passed to the JVM as an argument.
set "JVM_CONFIG_ARGS="
set "JVM_CONFIG=%MAVEN_PROJECTBASEDIR%\.mvn\jvm.config"
if exist "%JVM_CONFIG%" (
  for /f "usebackq delims=" %%L in ("%JVM_CONFIG%") do (
    if not "%%L"=="" set "JVM_CONFIG_ARGS=!JVM_CONFIG_ARGS! %%L"
  )
)
if defined JVM_CONFIG_ARGS set "MAVEN_OPTS=!JVM_CONFIG_ARGS! %MAVEN_OPTS%"

where mvn >nul 2>nul
if errorlevel 1 goto :no_maven

if not defined JAVA_HOME echo WARNING: JAVA_HOME is not set. Relying on 'java' from PATH.

mvn -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" %*
exit /b %errorlevel%

:no_maven
echo ERROR: 'mvn' was not found on PATH.
echo        Install Maven 3.9.9+ ^(for example: choco install maven --version=3.9.9^)
echo        or run ./mvnw from Git Bash / WSL, which downloads Maven for you.
exit /b 1