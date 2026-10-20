# TunePrint

## Opis aplikacji

TunePrint to wieloosobowa gra muzyczna, w której użytkownicy dołączają do wspólnego
pokoju i logują się za pomocą Spotify. Aplikacja pobiera ich najczęściej słuchane
utwory z ustalonego okresu.

Podczas rozgrywki odtwarzane są krótkie fragmenty piosenek, a gracze odgadują,
do czyjego top 100 należy dany utwór, a zależnie od trybu również jaki jest jego
tytuł oraz kto jest wykonawcą. Wygrywa osoba, która odgadnie największą liczbę
elementów w najkrótszym czasie.

## Wybrane technologie

- Kotlin Multiplatform — Android i iOS.
- Compose Multiplatform — interfejs użytkownika.
- Spotify Web API — pobieranie danych o utworach.
- Ktor + WebSockets — backend i komunikacja w czasie rzeczywistym.
- PostgreSQL — baza danych.

## Wymagania funkcjonalne

- Logowanie przez Spotify.
- Tworzenie pokoi i dołączanie do rozgrywki.
- Losowanie i odtwarzanie fragmentów utworów.
- Zgadywanie właściciela, tytułu i wykonawcy.
- System punktacji i ranking graczy.

## Wymagania niefunkcjonalne

- Obsługa Androida i (być może również) iOS.
- Intuicyjny, estetyczny i responsywny interfejs.
- Synchronizacja rozgrywki w czasie rzeczywistym.
- Bezpieczna autoryzacja i przechowywanie danych użytkowników.
