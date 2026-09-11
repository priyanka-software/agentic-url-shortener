@echo off
setlocal
set "DIR=%~dp0"
set "JAR=%DIR%gradle\wrapper\gradle-wrapper.jar"
if exist "%JAR%" (
  java -classpath "%JAR%" org.gradle.wrapper.GradleWrapperMain %*
  exit /b %ERRORLEVEL%
)
set "GV=8.10.2"
set "HOME=%DIR%.gradle-bootstrap"
set "GRADLE=%HOME%\gradle-%GV%\bin\gradle.bat"
if not exist "%GRADLE%" (
  echo First run: downloading Gradle %GV%...
  if not exist "%HOME%" mkdir "%HOME%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-%GV%-bin.zip' -OutFile '%HOME%\gradle.zip'; Expand-Archive -Force '%HOME%\gradle.zip' '%HOME%'"
  if errorlevel 1 exit /b 1
)
call "%GRADLE%" %*
