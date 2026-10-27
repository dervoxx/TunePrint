# Uruchomienie Androida

Otwórz katalog główny `TunePrint/` w Android Studio obsługującym AGP 8.11.1
(Narwhal 2025.1.1 lub nowszym). Poczekaj na synchronizację Gradle.

- Ustaw Gradle JDK na JDK 17 lub 21 (może być wbudowany JDK Android Studio).
- Zainstaluj Android SDK Platform 36 przez SDK Manager.
- Wybierz konfigurację `composeApp`, emulator lub telefon z Androidem 7.0
  (API 24) lub nowszym i naciśnij Run.

Android Studio zapisze lokalną ścieżkę SDK w ignorowanym `local.properties`.
Pierwsza synchronizacja wymaga internetu do pobrania Gradle i zależności.

Budowanie z terminala, po skonfigurowaniu JDK i SDK:

```sh
./gradlew :composeApp:assembleDebug
```

Na Windows użyj `gradlew.bat :composeApp:assembleDebug`.
APK powstaje w `composeApp/build/outputs/apk/debug/composeApp-debug.apk`.

Wspólny ekran Compose znajduje się w `composeApp/src/commonMain/`,
a aktywność i manifest Androida w `composeApp/src/androidMain/`.
Na razie skonfigurowany jest tylko Android; projekt nie zawiera jeszcze rozgrywki.
