# Top utwory gracza

W Spotify Developer Dashboard wybierz aplikację TunePrint z dostępem do Web API. W **Settings → Redirect URIs** dodaj dokładnie `https://tuneprint.mliczek.com/spotify/callback` i zapisz ustawienia. Android otwiera ten link w TunePrint. Weryfikacja domeny wymaga pliku `/.well-known/assetlinks.json` z odciskiem certyfikatu podpisującego APK.

W trybie deweloperskim właściciel aplikacji potrzebuje Spotify Premium. W **Settings → Users Management** dodaj konta uczestników. Standardowy limit wynosi pięć kont. [Tryb deweloperski](https://developer.spotify.com/documentation/web-api/concepts/quota-modes)

Przed zbudowaniem APK wpisz `spotifyClientId=<Client ID>` w lokalnym pliku `local.properties`. Możesz też ustawić zmienną środowiskową `SPOTIFY_CLIENT_ID` lub właściwość Gradle `spotifyClientId`. Uruchom `./gradlew :composeApp:assembleDebug`. Logowanie korzysta z PKCE i zgody `user-top-read`. Kod autoryzacyjny i token pozostają w pamięci telefonu na czas importu. [Logowanie PKCE](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow)

Po udzieleniu zgody w przeglądarce wróć do TunePrint. Aplikacja pobiera trzy rankingi Spotify: długoterminowy (około roku), ostatnie półrocze i ostatnie cztery tygodnie. Liczba pozycji zależy od historii konta. Dla każdego okresu TunePrint przechodzi ranking od góry do uzyskania 50 dopasowań z fragmentem lub wyczerpania listy. [Muzyka w rundach](catalog.md). [Top utwory Spotify](https://developer.spotify.com/documentation/web-api/reference/get-users-top-artists-and-tracks)
