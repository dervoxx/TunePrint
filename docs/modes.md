# Tryby rund

Każda runda ma jedno zadanie. Serwer pokazuje poprawną odpowiedź i przyznane punkty po zakończeniu rundy.

- **OWNERS** — zaznacz wszystkich graczy, do których należy utwór. Pełny zestaw daje poprawną odpowiedź. Przy częściowym trafieniu serwer odejmuje od odsetka wskazanych właścicieli odsetek błędnie wskazanych osób wśród pozostałych graczy. Najniższa ocena to zero.
- **TITLE** — wpisz pełny tytuł utworu.
- **ARTIST** — wpisz pełne pole wykonawcy w kolejności z katalogu. Dla „Bedoes 2115 / Lanek” odpowiedź „bedoes 2115 lanek” jest poprawna. Samo „Bedoes 2115” daje zero punktów.

W odpowiedziach tekstowych serwer pomija wielkość liter, polskie znaki diakrytyczne i apostrofy. Odstępy i inne znaki rozdzielające wyrazy traktuje jak jedną spację. Tryb tekstowy wymaga tytułu lub wykonawcy zawierającego przynajmniej jedną literę lub cyfrę. Utwór bez wykonawcy może więc pojawić się w rundzie TITLE lub OWNERS.

Za pełne trafienie można dostać od 100 punktów na początku do 60 po 15 sekundach. Serwer mnoży tę wartość przez dokładność odpowiedzi i zaokrągla wynik do liczby całkowitej.
