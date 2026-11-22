# Top utwory gracza

W Spotify Developer Dashboard wybierz aplikację z dostępem do Web API. W **Settings → Redirect URIs** wpisz dokładnie `http://127.0.0.1/spotify/callback`. Spotify pozwala pominąć port przy rejestracji adresu loopback. TunePrint przy każdym logowaniu otwiera wolny port na telefonie. [Przekierowania Spotify](https://developer.spotify.com/documentation/web-api/concepts/redirect_uri)

W trybie deweloperskim właściciel aplikacji potrzebuje Spotify Premium. W **Settings → Users Management** dodaj konta uczestników. Standardowy limit wynosi pięć kont. [Tryb deweloperski](https://developer.spotify.com/documentation/web-api/concepts/quota-modes)

Przed zbudowaniem APK wpisz `spotifyClientId=<Client ID>` w lokalnym pliku `local.properties`. Możesz też ustawić zmienną środowiskową `SPOTIFY_CLIENT_ID` lub właściwość Gradle `spotifyClientId`. Uruchom `./gradlew :composeApp:assembleDebug`. Logowanie korzysta z PKCE i zgody `user-top-read`. Kod autoryzacyjny i token pozostają w pamięci telefonu na czas importu. [Logowanie PKCE](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow)

Po udzieleniu zgody w przeglądarce wróć do TunePrint. Aplikacja pobiera do 100 utworów z rankingu Spotify za ostatnie około sześć miesięcy, po 50 na stronę. Liczba pozycji zależy od historii konta. TunePrint automatycznie wyszukuje fragmenty do rund. [Muzyka w rundach](catalog.md). [Top utwory Spotify](https://developer.spotify.com/documentation/web-api/reference/get-users-top-artists-and-tracks)
