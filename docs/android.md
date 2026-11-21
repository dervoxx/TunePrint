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
Ekran startowy otwiera grę ze znajomymi. Przycisk **Zagraj offline** uruchamia
lokalną grę z trzema symulowanymi przeciwnikami.

## Gra online

1. Przy pierwszym uruchomieniu aplikacja korzysta z `wss://tuneprint.mliczek.com/game`.
   W starszej instalacji możesz wybrać **Adres serwera** i wpisać ten adres.
2. Wpisz nick i naciśnij **Wybierz utwory do gry**. Po wejściu do pokoju aplikacja
   zapamięta nick, adres i playlistę na tym urządzeniu. Utwórz pokój lub dołącz
   przez listę albo kod. Gospodarz może udostępnić kod, wybrać tryb
   (właściciele, tytuł lub wykonawca) i rozpocząć grę przy co najmniej trzech osobach.

Do testów lokalnych uruchom [serwer](server.md). W emulatorze wpisz
`ws://10.0.2.2:8080/game`, a na telefonie adres LAN z konsoli serwera.

Każde urządzenie odtwarza nagrania z własnego APK, dlatego zbuduj je z tym samym
kompletem plików `songs/`. Podczas rundy serwer otrzymuje wybór lub wpisany tekst.
Po upływie czasu ocenia ostatnią niepustą odpowiedź roboczą, o ile nadal jest zaznaczona
lub wpisana. Wyniki trafiają do graczy po zakończeniu rundy.
Gra trwa po schowaniu aplikacji. Po powrocie audio wraca na bieżącą pozycję.
Po zerwaniu połączenia klient automatycznie próbuje wznowić sesję.
Systemowy przycisk Wstecz pozwala opuścić pokój.

## Nagrania i przebieg gry offline

Umieść lokalne pliki audio w ignorowanym przez Git katalogu `songs/`.
Nazwy plików, przypisania w grze offline i początek fragmentów definiuje
`RoundTracks.kt`. Metadane opisuje [katalog demonstracyjny](catalog.md). Gradle dołącza nagrania do APK;
nie są częścią repozytorium. Używaj plików, które możesz wykorzystać
w danym zastosowaniu.

Przed utworzeniem lub dołączeniem do pokoju wpisz nick. Domyślnie grasz jako
Olek; pozostali gracze to Kacper, Kamil i Ziobro.
Możesz utworzyć pokój albo wybrać dostępny pokój lub wpisać jego sześciocyfrowy kod. Po utworzeniu
pozostali gracze pojawiają się w ciągu trzech sekund; po dołączeniu są już obecni.
Gospodarz ustawia limit 3–8 osób i liczbę rund do liczby dostępnych nagrań
(maksymalnie 10). Start wymaga co najmniej trzech obecnych osób, nie pełnego pokoju.
Nagrania w jednej grze nie powtarzają się. Gospodarz może też włączyć
pokazywanie liczby playlist zawierających utwór; domyślnie jest ukryta.

Po odliczaniu 3–2–1 masz 15 sekund. Zaznacz jednego lub kilku graczy i zatwierdź.
Przy upływie czasu niepusty wybór zostaje zatwierdzony automatycznie.
Punkty uwzględniają trafienia, błędne zaznaczenia i czas:

- trafność = udział trafionych właścicieli minus udział wskazanych niewłaścicieli,
  z minimum 0;
- punkty = trafność × (60 + 40 × udział pozostałego czasu), po zaokrągleniu;
- pełne trafienie daje 60–100 pkt, połowa poprawności 30–50 pkt;
- zaznaczenie wszystkich przy obecności niewłaścicieli daje 0.

Runda kończy się po odpowiedzi wszystkich albo upływie czasu. Dopiero wtedy
widać wszystkie poprawne osoby i odpowiedzi graczy. Nagranie „Halo halo z aresztu”
ma zawsze Ziobro i występuje dokładnie raz w każdej grze, na losowej pozycji.
Ziobro pozostaje w składzie również przy limicie trzech osób. Pozostałe
przypisania mogą obejmować kilka osób.

Między rundami wyniki pozostają przez maksymalnie 30 sekund; gotowość wszystkich
skraca przerwę. Po ostatniej rundzie od razu pojawiają się końcowe wyniki
i animowany ranking, bez gotowości ani odliczania. Audio i zegar zatrzymują się
po schowaniu aplikacji; obrót ekranu zachowuje grę.

Testy logiki:

```sh
./gradlew :composeApp:testDebugUnitTest
```
