@echo off
rem Starts the E-Invoice Generator. The lib directory with the JAR and the libraries must be in the same
rem directory as this script. The build replaces the version in the name of the JAR.
rem Uses %JAVA_HOME%\bin\java.exe if JAVA_HOME is set, otherwise "java" from the PATH (Java 17 or newer).
rem
rem Usage: e-invoice-generator [--config <directory>] [--help] [--version]

setlocal

set "JAR=%~dp0lib\e-invoice-generator-@project.version@.jar"
if not exist "%JAR%" (
    echo JAR not found in the lib directory next to this script: %JAR% 1>&2
    exit /b 1
)

if defined JAVA_HOME (
    set "JAVA=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA=java"
)

rem UTF-8 code page, so that umlauts and symbols like the check mark are shown correctly
chcp 65001 >nul

"%JAVA%" -jar "%JAR%" %*
exit /b %ERRORLEVEL%
