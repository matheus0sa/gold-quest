# Gold Quest

To-do list onde cada tarefa concluída dá gold para gastar numa loja de recompensas.
Os preços da loja sobem com o uso frequente e entram em promoção quando um item fica esquecido.

## Rodar no Windows

1. Instale o Python em https://www.python.org/downloads/ (marque "Add python.exe to PATH").
2. Dê dois cliques em `iniciar.bat`. Na primeira vez ele cria o ambiente e instala tudo.
3. Abra http://localhost:5000

Para acessar pelo celular, conecte-o no mesmo Wi-Fi e abra http://IP-DO-PC:5000
(veja o IP com `ipconfig`, em "Endereço IPv4"). Se o Windows perguntar sobre o firewall,
permita o acesso em redes privadas.

## Rodar no Linux

```bash
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python app.py
```

## Contas

Cada pessoa cria a própria conta em "Criar conta" e tem tarefas, loja, inventário e gold
separados. Se você já usava o app antes do login existir, a **primeira conta criada** recebe
todos os dados antigos.

A chave que assina os logins é gerada sozinha no arquivo `.secret_key` (não vai para o git).
Apagar esse arquivo desconecta todo mundo. Se preferir, defina a variável `SECRET_KEY`.

## Dados

Tudo fica em `gold_quest.db`, criado na primeira execução. Para levar seus dados para
outro computador, copie esse arquivo para a pasta do projeto. Para zerar tudo, apague-o.

## Ajustes

- Recompensa por dificuldade: `models.py` (`DIFFICULTY_REWARDS`)
- Preços dinâmicos: constantes no topo de `pricing.py`
- Modo debug: defina `FLASK_DEBUG=1` antes de rodar
