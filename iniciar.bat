@echo off
title Gold Quest
cd /d "%~dp0"

if not exist venv (
    echo Primeira execucao: criando ambiente e instalando dependencias...
    py -m venv venv
)
call venv\Scripts\activate.bat
rem Instala o que faltar (rapido quando ja esta tudo instalado)
pip install -q -r requirements.txt

echo.
echo Gold Quest rodando em http://localhost:5000
echo Feche esta janela para parar.
echo.
python app.py
pause
