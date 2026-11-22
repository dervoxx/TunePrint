# Muzyka w rundach

Android pobiera ranking gracza za jego zgodą. Do serwera gry wysyła identyfikatory
utworów, tytuły i wykonawców. Token Spotify służy wyłącznie do importu na telefonie.

Serwer sprawdza do 12 pierwszych unikalnych utworów z rankingu i dobiera
maksymalnie 5 różnych nagrań z fragmentem w polskim katalogu Apple.
Pomija pozycje bez dopasowania. Wspólny utwór może należeć do kilku uczestników.
Klient odtwarza fragment przez HTTPS. APK nie wymaga plików z katalogu `songs/`.

Standardowe warunki iTunes Search API ograniczają wykorzystanie podglądów do promocji
treści i wyłączają niezależne zastosowania rozrywkowe. Użycie podglądów w quizie
wymaga uprawnień obejmujących ten sposób odtwarzania.
[Warunki Apple](https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/)
