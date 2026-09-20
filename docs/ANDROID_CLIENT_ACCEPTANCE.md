# Android AI Studio — kryteria ukończenia klienta

Ten dokument oddziela funkcje obecne w kodzie od wymagań, które trzeba zweryfikować przed uznaniem aplikacji za gotową.

## Obecny zakres klienta

- Wybór trybu IMAGE/VIDEO.
- Pobieranie katalogu workflow przez `GET /api/v2/workflows`.
- Dynamiczne renderowanie parametrów, w tym list wyboru i wartości logicznych.
- Walidacja typów, wymaganych pól, zakresów liczbowych i długości tekstu po stronie klienta.
- Wysyłanie zadań przez `POST /api/v2/jobs`.
- Odczyt statusu zadania oraz endpointu wyniku jest zadeklarowany w warstwie Retrofit.
- Klucz API jest przechowywany w stanie ekranu, a nie w trwałym magazynie.
- Klient wymaga HTTPS dla adresu backendu.

## Braki funkcjonalne widoczne w ekranie

- Po zaakceptowaniu zadania ekran pokazuje jedynie identyfikator i status; nie odświeża automatycznie statusu.
- Nie ma jawnej akcji ponownego odczytu statusu ani historii zadań.
- Nie ma prezentacji wyników jako podglądów mediów.
- Wartość `progress` z API jest typu `Double`; przed wyświetleniem jako procent należy ustalić i zachować kontrakt skali (0–1 albo 0–100). Backend powinien być źródłem prawdy.
- Błędy walidacji backendu (np. szczegóły HTTP 422) wymagają czytelnego mapowania do komunikatu użytkownika.

## Kryteria akceptacji — następna implementacja

1. Po utworzeniu zadania zachować jego ID i rozpocząć odpytywanie `GET /api/jobs/{id}` z limitem czasu oraz przerwą między żądaniami.
2. Zatrzymać odpytywanie dla statusów terminalnych (co najmniej `COMPLETED` i `FAILED`) oraz po anulowaniu korutyny.
3. Pokazywać postęp z poprawną konwersją skali, stan oczekiwania, błąd i timeout.
4. Umożliwić ręczne odświeżenie zadania po timeout lub utracie połączenia.
5. Po ukończeniu pobrać `GET /api/jobs/{id}/result` i pokazać metadane wszystkich wyników.
6. Nie konstruować adresów pobierania mediów, dopóki backend nie udostępni i nie udokumentuje bezpiecznego endpointu proxy/serwowania plików.
7. Dodać testy jednostkowe walidacji parametrów oraz testy kontraktowe DTO/endpointów.
8. Zweryfikować kompilację debug/release i działanie z rzeczywistym backendem ComfyUI.

## Weryfikacja przed wydaniem

- [ ] `./gradlew test`
- [ ] `./gradlew assembleDebug`
- [ ] `./gradlew assembleRelease`
- [ ] Test HTTPS i błędnego certyfikatu.
- [ ] Test klucza API błędnego/brakującego.
- [ ] Test workflow bez parametrów, z parametrami wymaganymi i z wartościami spoza zakresu.
- [ ] Test zadania zakończonego sukcesem, błędem i przekroczeniem limitu czasu.
- [ ] Test odtworzenia stanu po zmianie konfiguracji/odtworzeniu ekranu.
- [ ] Test na urządzeniu fizycznym z backendem ComfyUI.

## Bezpieczeństwo

- Nie umieszczać klucza API w repozytorium, logach ani komunikatach błędów.
- W produkcji używać HTTPS z poprawną walidacją certyfikatu; nie wyłączać weryfikacji TLS.
- Wyniki mediów udostępniać przez kontrolowany backend z autoryzacją, nie przez dowolnie konstruowane URL-e.
- Ograniczyć rozmiar promptów i parametrów zgodnie z kontraktem backendu.
- Generowanie treści musi respektować prawo oraz zasady bezpieczeństwa dotyczące osób i materiałów.

## Status

Dokumentacja kryteriów nie oznacza, że powyższe testy zostały wykonane. Status kompilacji i integracji wymaga wyników z Android SDK/Gradle oraz działającego backendu.
