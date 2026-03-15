@echo off
REM Run from project folder (GCM_S). Uses MySQL 8.0 default install path.
set MYSQL_BIN=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe
if not exist "%MYSQL_BIN%" (
    echo MySQL not found at %MYSQL_BIN%
    echo Edit this file if MySQL is installed elsewhere.
    pause
    exit /b 1
)
echo Running dummy_db.sql (you will be prompted for MySQL root password)...
"%MYSQL_BIN%" -u root -p < "%~dp0dummy_db.sql"
if %ERRORLEVEL% equ 0 (
    echo.
    echo Database gcm_db setup complete.
) else (
    echo.
    echo Script failed. Check your MySQL password and that MySQL service is running.
)
pause
