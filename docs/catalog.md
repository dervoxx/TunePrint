# Muzyka w rundach

Android pobiera ranking gracza za jego zgodą. Do serwera gry wysyła identyfikatory
utworów, tytuły i wykonawców. Token Spotify służy wyłącznie do importu na telefonie.

Serwer sprawdza kolejne pozycje trzech rankingów każdego gracza w polskim katalogu Apple. Na początku
meczu losuje z połączonej puli dla okresu wybranego przez gospodarza tyle różnych nagrań, ile rund ustawił gospodarz.
Pomija pozycje bez dopasowania. Wspólny utwór może należeć do kilku uczestników.
Klient odtwarza fragment przez HTTPS. APK nie wymaga plików z katalogu `songs/`.

Zespół TunePrint potwierdza, że otrzymał od Apple mailową zgodę na używanie
fragmentów w tej aplikacji uczelnianej.
