@echo off
setlocal
if not exist backups mkdir backups
for /f "tokens=1-4 delims=/ " %%a in ("%date%") do set "stamp=%%a%%b%%c"
copy /Y data\core-ai.db backups\core-ai-%stamp%.db
