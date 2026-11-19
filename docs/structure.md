# Struktura

- `composeApp/` — aplikacja Kotlin Multiplatform z Compose, obecnie dla Androida.
  `src/commonMain/` zawiera wspólny interfejs, `src/androidMain/` kod Androida.
- `server/` — serwer Ktor. Zarządza pokojami, czasem, odpowiedziami i rankingiem.
- `shared/` — typy wiadomości wymienianych przez Androida i serwer.
- `gradle/`, `gradlew`, `gradlew.bat` — Gradle Wrapper; konfiguracja budowania
  znajduje się w katalogu głównym i module aplikacji.
- `docs/` — dokumentacja, w tym [uruchomienie Androida](android.md).
