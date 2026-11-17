# Gold Quest

To-do list onde cada tarefa concluída dá gold para gastar numa loja de recompensas.
Os preços da loja sobem com o uso frequente e entram em promoção quando um item fica esquecido.

## Joias

Além do gold, existem joias: Diamante, Esmeralda, Rubi, Safira, Ametista e Topázio. Cada tarefa
pode dar uma joia (ex.: Treino = 30 gold + 1 Esmeralda), e cada recompensa da loja pode pedir joias
além do gold (ex.: Jogatina de GTA = 50 gold + 1 Esmeralda). Sem a joia não dá para comprar, mesmo
com gold sobrando: para jogar, é preciso treinar.

- As joias têm preço fixo; só o gold da loja varia com o uso.
- Não há troca entre joias nem compra de joias com gold.
- A penalidade por não fazer uma tarefa tira só gold, nunca joias.

## App Android

A pasta [android/](android/) tem o app nativo (Kotlin, Jetpack Compose e Room). Ele funciona
offline e guarda tudo no próprio celular, sem contas nem servidor. Inclui o lembrete diário
(sino no topo do app): avisa no horário escolhido o que ainda falta fazer hoje.

Para abrir, use o Android Studio na pasta `android/`. Para gerar o APK pela linha de comando
(o JDK do Android Studio serve como `JAVA_HOME`):

```bash
cd android
./gradlew assembleDebug      # APK em app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest  # testes das regras (preços, joias, revisão, lembrete)
```

A versão 1.3 (joias) recomeça o banco do zero: instalar por cima apaga os dados das versões anteriores.
