@echo off
setlocal
set "JAVA_HOME=C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.1\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"
mvn spring-boot:run
