# Struktura

- `composeApp/` — aplikacja Kotlin Multiplatform z Compose, obecnie dla Androida.
  `src/commonMain/` zawiera wspólny interfejs, `src/androidMain/` kod Androida.
- `gradle/`, `gradlew`, `gradlew.bat` — Gradle Wrapper; konfiguracja budowania
  znajduje się w katalogu głównym i module aplikacji.
- `docs/` — dokumentacja, w tym [uruchomienie Androida](android.md).

`server/` (Ktor) i `iosApp/` (Xcode) dodamy wraz z ich implementacją.
