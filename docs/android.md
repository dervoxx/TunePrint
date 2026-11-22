# Uruchomienie Androida

Przy połączeniu z `tuneprint.mliczek.com` Android najpierw pyta systemowy DNS.
Jeśli ten nie znajdzie adresu, aplikacja ponawia zapytanie przez DNS-over-HTTPS Cloudflare.

Otwórz katalog główny `TunePrint/` w Android Studio obsługującym AGP 8.11.1
(Narwhal 2025.1.1 lub nowszym). Poczekaj na synchronizację Gradle.

- Ustaw Gradle JDK na JDK 17 lub 21. Jeśli wbudowany JDK Android Studio
  ma wersję 25 lub nowszą, wybierz osobny JDK 21 dla Gradle 8.13.
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

## Gra

1. Przy starcie zaloguj się do Spotify w przeglądarce i wróć do TunePrint.
   Konfigurację kont testowych opisuje [instrukcja Spotify](spotify.md).
2. Poczekaj, aż gra dobierze nagrania do Twoich najczęściej słuchanych utworów.
   Wpisz nick, utwórz pokój albo dołącz przez kod lub listę.
3. Gospodarz może rozpocząć grę, gdy w pokoju są co najmniej trzy osoby.
   Każdy telefon odtwarza ten sam fragment z internetu. Po powrocie z tła
   odtwarzanie uwzględnia czas pozostały w rundzie.

Domyślny adres to `wss://tuneprint.mliczek.com/game`. Do testów z [serwerem lokalnym](server.md)
w emulatorze użyj `ws://10.0.2.2:8080/game`. Testy aplikacji uruchamia
`./gradlew :composeApp:testDebugUnitTest`.
