# Gold Quest

To-do list onde cada tarefa concluída dá gold para gastar numa loja de recompensas.
Os preços da loja sobem com o uso frequente e entram em promoção quando um item fica esquecido.

## App Android

A pasta [android/](android/) tem o app nativo (Kotlin, Jetpack Compose e Room). Ele funciona
offline e guarda tudo no próprio celular, sem contas nem servidor. Inclui o lembrete diário
(sino no topo do app): avisa no horário escolhido o que ainda falta fazer hoje.

Para abrir, use o Android Studio na pasta `android/`. Para gerar o APK pela linha de comando
(o JDK do Android Studio serve como `JAVA_HOME`):

```bash
cd android
./gradlew assembleDebug      # APK em app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest  # testes das regras (preços, revisão, lembrete)
```
