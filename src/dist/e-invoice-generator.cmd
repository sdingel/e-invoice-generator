@echo off
rem Starts the E-Invoice Generator. The JAR must be in the same directory as this script.
rem Uses %JAVA_HOME%\bin\java.exe if JAVA_HOME is set, otherwise "java" from the PATH (Java 17 or newer).
rem
rem Usage: e-invoice-generator [--config <directory>] [--help]

setlocal

set "JAR=%~dp0e-invoice-generator.jar"
if not exist "%JAR%" (
    echo e-invoice-generator.jar not found next to this script: %JAR% 1>&2
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
