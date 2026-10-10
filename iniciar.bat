@echo off
title Gold Quest
cd /d "%~dp0"

if not exist venv (
    echo Primeira execucao: criando ambiente e instalando dependencias...
    py -m venv venv
    call venv\Scripts\activate.bat
    pip install -r requirements.txt
) else (
    call venv\Scripts\activate.bat
)

echo.
echo Gold Quest rodando em http://localhost:5000
echo Feche esta janela para parar.
echo.
python app.py
pause
