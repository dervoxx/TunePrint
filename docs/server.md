# Serwer gry

W katalogu projektu uruchom `./gradlew :server:run` (Windows: `.\gradlew.bat :server:run`).
Serwer nasłuchuje pod `ws://localhost:8080/game`. Port można zmienić zmienną
`PORT`. Emulator Androida łączy się z `ws://10.0.2.2:8080/game`, a urządzenie
w tej samej sieci Wi-Fi używa adresu LAN komputera. Do udostępnienia usługi
poza siecią lokalną potrzebny jest szyfrowany transport `wss://`.

Paczkę do uruchomienia na innym komputerze tworzy `./gradlew :server:distZip`.
Rozpakuj `server/build/distributions/server.zip` i uruchom `server/bin/server.bat`
na Windows albo `server/bin/server` na Linuxie z Javą 17 lub nowszą.

## Przebieg połączenia

- Jedna ramka tekstowa zawiera jedno polecenie JSON. Typy wiadomości definiuje
  [Protocol.kt](../shared/src/main/kotlin/pl/tuneprint/net/Protocol.kt).
  `list` zwraca wolne pokoje. `create` i `join` przyjmują nazwę i pole
  `tracks` z metadanymi Spotify. Dołączenie wymaga również sześciocyfrowego kodu.
  Serwer przeszukuje polski katalog iTunes, dla pełnego Top 50 każdego gracza.
  Przy starcie losuje z połączonej puli jeden różny utwór na każdą ustawioną rundę. Potrzebne jest co najmniej
  jedno dopasowanie. Opcjonalnie `import` przyjmuje tę samą listę i zwraca
  zdarzenie `catalog` z dostępnymi `catalogKeys` do użycia na tym połączeniu.
- Pierwszy komunikat `state` zawiera prywatny `token` i `playerId` obok stanu
  pokoju. Po utracie połączenia klient ma 120 s na `resume` z tym tokenem.
  Wznowienie zastępuje poprzednie połączenie. `leave` natychmiast unieważnia
  token. Po odejściu gospodarza jego rolę przejmuje kolejny uczestnik.
- Gospodarz wybiera w lobby tryb, limit miejsc (3–8), liczbę rund i wskazówkę
  o liczbie właścicieli. Start wymaga co najmniej trzech połączonych osób.
  Serwer losuje bez powtórzeń z katalogów graczy,
  uwzględniając metadane wymagane w wybranym trybie.
- Po 3 s odliczania uczestnicy mają 15 s na odpowiedź. Polecenia `draft`,
  `answer` i `ready` wymagają bieżącego `roundNumber`. Odpowiedzią jest
  `ownerIds`, `title` lub `artist`, zależnie od trybu. `draft` aktualizuje
  prywatny wybór, a pusty wybór go usuwa. `answer` zatwierdza odpowiedź raz.
  Po upływie czasu serwer ocenia ostatni niepusty szkic z czasem 15 s.
- W trakcie rundy serwer podaje losowy klucz audio, adres HTTPS fragmentu Apple i identyfikatory osób, które
  odpowiedziały. Rozwiązanie, odpowiedzi i nowe punkty publikuje razem po
  rundzie. Kolejne odliczanie rusza po gotowości wszystkich lub po 30 s.
  Ostatnia runda prowadzi od razu do finału. Gospodarz może wrócić do lobby
  przez `replay`.

Pokoje i wyniki istnieją w pamięci jednego procesu. Restart kończy sesje.
Odejście w trakcie meczu zachowuje gracza w wynikach i wśród właścicieli
do końca rozgrywki. Pozostali mogą przejść dalej po własnych odpowiedziach.
Serwer usuwa pokój po odejściu lub wygaśnięciu sesji ostatniej osoby.

Testy reguł i protokołu uruchamia `./gradlew :server:test`.

## Cloudflare Tunnel

Na hoście tunelu ustaw serwerowi `HOST=127.0.0.1` i wolny `PORT`.
W Cloudflare wskaż usługę `http://127.0.0.1:PORT`. W aplikacji w polu
**Adres serwera** wpisz `wss://<publiczna-nazwa>/game`.
Do zbudowania APK z tym adresem na pierwszym uruchomieniu służy
`./gradlew :composeApp:assembleDebug -PtuneprintPublicHost=<publiczna-nazwa>`.
Adres wybrany wcześniej na telefonie ma pierwszeństwo.
