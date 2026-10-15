@echo off
rem Oson Aloqa Server - тестовый режим (PGMEM, данные в RAM)
rem Для production: см. DEPLOY.md (PostgreSQL + домен + HTTPS)
cd /d "%~dp0"
if not exist dist (npm install && npm run build)
set PGMEM=1
set PORT=8080
set JWT_SECRET=change_me_local_only_abc123def456ghi
echo Oson Aloqa Server: http://0.0.0.0:8080
node dist\index.js
pause

