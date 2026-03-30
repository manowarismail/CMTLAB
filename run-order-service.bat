@echo off
setlocal
cd /d "%~dp0"

set "CP=bin;lib\mysql-connector-j-8.3.0.jar;C:\Users\HP\quickfix\org.quickfixj-2.3.1\quickfixj-all-2.3.1.jar;C:\Users\HP\quickfix\org.quickfixj-2.3.1\quickfixj-messages-all-2.3.1.jar;C:\Users\HP\slf4j-api-1.7.36.jar;C:\Users\HP\slf4j-simple-1.6.1.jar;C:\Users\HP\apache-mina-2.0.27\dist\mina-core-2.0.27.jar;C:\Users\HP\quickfix\org.quickfixj-2.3.1\quickfixj-messages-fix44-2.3.1.jar;C:\Users\HP\OneDrive\Documents\Downloads\gson-2.8.9.jar;C:\Users\HP\OneDrive\Documents\Downloads\Java-WebSocket-1.5.5.jar"

if not exist "bin\OrderService\AppLauncher.class" (
  echo Compiling into bin\ ...
  if not exist bin mkdir bin
  "C:\Program Files\Java\jdk-21\bin\javac.exe" -d bin -cp "%CP%" src\OrderService\*.java
  if errorlevel 1 exit /b 1
)

java -cp "%CP%" OrderService.AppLauncher
endlocal
