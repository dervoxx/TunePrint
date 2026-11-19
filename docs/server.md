# Serwer gry

W katalogu projektu uruchom `./gradlew :server:run` (Windows: `.\gradlew.bat :server:run`).
Serwer nasłuchuje pod `ws://localhost:8080/game`. Port można zmienić zmienną
`PORT`. Emulator Androida łączy się z `ws://10.0.2.2:8080/game`, a urządzenie
w tej samej sieci Wi-Fi używa adresu LAN komputera. Do udostępnienia usługi
poza siecią lokalną potrzebny jest szyfrowany transport `wss://`.

## Przebieg połączenia

- Jedna ramka tekstowa zawiera jedno polecenie JSON. Typy wiadomości definiuje
  [Protocol.kt](../shared/src/main/kotlin/pl/tuneprint/net/Protocol.kt).
  `list` zwraca wolne pokoje. `create` i `join` wymagają nazwy i niepustej
  listy `catalogKeys`. Dołączenie wymaga również sześciocyfrowego kodu.
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
- W trakcie rundy serwer podaje klucz audio i identyfikatory osób, które
  odpowiedziały. Rozwiązanie, odpowiedzi i nowe punkty publikuje razem po
  rundzie. Kolejne odliczanie rusza po gotowości wszystkich lub po 30 s.
  Ostatnia runda prowadzi od razu do finału. Gospodarz może wrócić do lobby
  przez `replay`.

Pokoje i wyniki istnieją w pamięci jednego procesu. Restart kończy sesje.
Odejście w trakcie meczu zachowuje gracza w wynikach i wśród właścicieli
do końca rozgrywki. Pozostali mogą przejść dalej po własnych odpowiedziach.
Serwer usuwa pokój po odejściu lub wygaśnięciu sesji ostatniej osoby.

Testy reguł i protokołu uruchamia `./gradlew :server:test`.
