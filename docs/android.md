# Uruchomienie Androida

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
Skonfigurowany jest tylko Android. Obecna gra działa lokalnie z trzema
symulowanymi przeciwnikami; backend i Spotify nie są jeszcze podłączone.

## Nagrania i przebieg gry

Przed budowaniem umieść w lokalnym, ignorowanym przez Git katalogu `songs/`:

- `14 - TACO HEMINGWAY - FRASCATI.mp3` — Kacper,
- `06 Fiji.mp3` — Kamil,
- `6. Madagaskar.mp3` — Ziobro.

Gradle dołącza zawartość tego katalogu jako assets APK. Nagrania nie są częścią
repozytorium. Katalog utworów i początek fragmentów definiuje `RoundTracks.kt`.
Nie zmieniaj nazw plików bez aktualizacji katalogu. Używaj nagrań, które możesz
wykorzystać w danym zastosowaniu.

Skład jest stały: Olek, Kacper, Kamil i Ziobro. Sterujesz Olkiem.
Rozpocznij grę. Po odliczaniu 3–2–1 masz 15 sekund na jeden wybór
właściciela utworu. Trafienie daje od 100 do 25 punktów, liniowo zależnie od czasu;
błąd lub brak odpowiedzi daje 0. Runda kończy się po odpowiedzi wszystkich lub
upływie czasu. Dopiero wtedy widać właściciela utworu i odpowiedzi graczy.

Wyniki pozostają przez maksymalnie 30 sekund. Przycisk gotowości skraca przerwę,
gdy wszyscy gracze będą gotowi. Po trzech rundach pojawia się końcowy ranking.
Audio i zegar zatrzymują się po schowaniu aplikacji; obrót ekranu zachowuje grę.

Testy logiki:

```sh
./gradlew :composeApp:testDebugUnitTest
```
