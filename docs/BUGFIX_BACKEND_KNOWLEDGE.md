> Historische Serverarchitektur. Für die aktuelle eigenständige App gilt [LOCAL_ANDROID.md](LOCAL_ANDROID.md).

# Backend-Verbindung und dauerhafte Wissensspeicherung

Stand: 8. November 2026. Ausgangspunkt: `dev` einschließlich PR #5. Navigation, Theme und bestehende Feed-/Wissens-Cards bleiben unverändert. Ergänzt werden nur notwendige Diagnosetexte in den Einstellungen.

## Ursachen und Grenzen der Diagnose

Auf diesem Entwicklungscomputer lief zu Beginn **kein PaperView-Backend**; es gab auch **keine ADB-Portweiterleitung**. Ein lokal gespeicherter OpenAI-Key startet keinen Server. Beide betroffenen Funktionen benötigen denselben erreichbaren, authentifizierten FastAPI-Server. Eine nicht erreichbare Backend-Adresse verhindert daher sowohl Modelltest als auch Wissensspeicherung. Die persönliche App-Konfiguration und ihre Schlüssel wurden nicht ausgelesen. Ob auf dem Smartphone tatsächlich eine falsche URL hinterlegt war, ist damit nicht bewiesen.

`localhost`/`127.0.0.1` bezeichnet auf dem Smartphone das Smartphone selbst. `10.0.2.2` ist ausschließlich der Android-Emulator. Eine LAN-IP mit HTTP genügt ebenfalls nicht: Private Dokumente und eigene Modellschlüssel benötigen HTTPS mit gültiger Zertifikatsprüfung. Der separate **Backend-Zugriffsschlüssel** entspricht `APP_TOKEN`; er ist nicht der OpenAI-Key.

Weitere im Code nachgewiesene Fehler:

- Android fasste DNS-, Verbindungs-, TLS- und Timeoutfehler pauschal zusammen. Der Modelltest prüfte nur den letzten Endpunkt und lieferte keine getrennte Diagnose der Verbindungskette.
- Das Backend unterschied Anbieter-403 nicht von einem ungültigen Key. Guthabenmangel (`429 insufficient_quota` oder 402) wurde mit gewöhnlichen Rate Limits vermischt. OpenAI erhielt immer `max_tokens` und `temperature=0`; bestimmte Modelle lehnen diese Parameter ab.
- Schreibantworten von `understood`, Konzeptbestätigung, PDF-Upload und Analyse wurden ignoriert. Ein anschließender GET konnte scheitern, obwohl SQLite bereits gespeichert hatte. Außerdem wurde erst der lokale Cache geschrieben und erst danach der UI-State aktualisiert. Cachefehler verdeckten daher ebenfalls einen bestätigten Servererfolg.
- Aktionen während eines laufenden Vorgangs wurden lautlos verworfen.

Keine nachgewiesene SQLite-Neustartlöschung: Profil, Dokumente, Konzepte, Quellenverbindungen und Feed blieben bei Rekonstruktion des Servers sowie einem tatsächlichen uvicorn-Prozessneustart erhalten. Der Datenpfad muss dauerhaft derselbe bleiben.

## Korrekturen

- Vier sichtbare Prüfschritte: `/health`, authentifiziertes `/v1/profile`, Anbieterantwort und tatsächliches Modellresultat über `/v1/llm/test`. Erst eine positive Modellantwort bestätigt den letzten Schritt. Anbieter-401, -403, Guthabenmangel, Rate Limit, Modell-/Parameterfehler und Serverfehler bestätigen lediglich die Erreichbarkeit des Anbieters.
- Feste deutsche Fehlermeldungen anhand von Fehlercodes. Fremde Antworttexte, Schlüssel, Header und Exception-Meldungen werden weder angezeigt noch geloggt. Sichere Logs enthalten nur Exception-Typ/HTTP-Status beziehungsweise feste Anbieterfehlercodes.
- OpenAI verwendet `max_completion_tokens` ohne erzwungene Temperatur. Andere kompatible Anbieter behalten `max_tokens`. Modellname und URL werden nicht stillschweigend ersetzt; der Anbietername in den Einstellungen ist eine Bezeichnung, keine automatische Endpunktauswahl. [Offizielle OpenAI-Parameter](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create), [Fehlercodes](https://developers.openai.com/api/docs/guides/error-codes).
- Android übernimmt die bestätigte Schreibantwort unmittelbar in die Wissenssammlung und den verschlüsselten Cache. Zusätzliche Aktualisierungen dürfen diesen Erfolg nicht zurücknehmen. Cacheprobleme und fehlgeschlagene Folgeabfragen werden gesondert gemeldet.
- Fehlgeschlagene primäre Schreibanfragen erzeugen keinen gespeicherten UI-Zustand. Ohne Verbindung gibt es keine vorgetäuschte Offline-Speicherung oder unsichtbare Warteschlange. Wiederholte Bestätigungen desselben Eintrags erzeugen keine doppelten Karten. Ein beschäftigter Zustand wird explizit gemeldet.
- Android-Keystore-/AES-GCM-Speicherung bleibt erhalten. Leere Entwürfe löschen den vorhandenen Schlüssel nicht. Ein optionaler Modelltest ist weiterhin vom lokalen Speichern getrennt.

## Nachweise

- Backend: **40 Tests erfolgreich**, einschließlich gültiger/ungültiger Modellantworten, 401/403/402/429/5xx, Guthaben/Rate-Limit-Trennung, Transportfehler, Timeouts, Parameterwahl, PDF-Verarbeitung, Bestätigung, Duplikaten, täglichen Feeds, SQLite-Neustart und Wiederverwendung neuen Wissens.
- Android: **21 JVM-Tests erfolgreich**, ein opt-in HTTP-Test übersprungen; Build/Lint erfolgreich, keine Lint-Fehler.
- Echtes A142-Smartphone, Android 16: **8 neue Speicher-/Netzwerktests erfolgreich**. Getestet wurden bestätigtes Speichern, nachfolgender GET-Fehler, Cachefehler, Nicht-Erreichbarkeit, HTTP-Fehler, Duplikate und ausdrücklich abgewiesene parallele Aktion.
- **Zwei HTTPS-Gerätephasen erfolgreich**, zusätzlich mit dem reproduzierbaren Testskript wiederholt: echte Retrofit-Aufrufe vom Smartphone zu FastAPI, TLS-Ketten- und Hostnamenprüfung, echte PDF-Textextraktion, Konzeptbestätigung, personalisierter Feed, Wissen speichern, App-Prozess beenden, Backend-Prozess neu starten, Daten erneut lesen, Folgetag generieren und gespeicherten Wissenseintrag im neuen Modellkontext nachweisen.
- Diese HTTPS-Tests verwenden ausschließlich künstliche PDF-/Profil-/Schlüsseldaten sowie ausdrücklich injizierte Paper- und **Modell-Mocks**. Sie belegen keinen Live-OpenAI-Aufruf. Ein privater Testschlüssel wurde nicht bereitgestellt; ein echter KI-Aufruf steht aus.
- Die sichtbaren Compose-/Navigationstests waren zunächst vom Sperrbildschirm blockiert. Nach Entsperren war die Wiederholung erfolgreich: **17 Gerätetests bestanden**, ein opt-in HTTPS-Test ohne Argumente übersprungen. Die zwei separaten HTTPS-Phasen wurden ausdrücklich ausgeführt und bestanden. Die persönliche Installation und ihre privaten Daten wurden nicht verändert.
- Der HTTPS-Durchlauf prüft die API-/Speicherstrecke auf dem Gerät; der vollständige manuelle Ablauf einschließlich Android-SAF-Dateiauswahldialog und Live-Anbieter ist damit noch nicht nachgewiesen.

Die Test-CA wird nur im Instrumentierungstest gezielt vertraut. Produktions- und Debug-App erhalten weder eine zusätzliche CA noch eine ausgeschaltete Hostnamenprüfung. Keine Mock-Endpunkte werden in die Produktionsfactory übernommen.

## Tests wiederholen

```sh
# Im Backend-venv, vom Projektverzeichnis:
PYTHONPATH=backend python -m pytest backend/tests -q

# JAVA_HOME und ANDROID_HOME passend zur Installation setzen.
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug

# Nur isolierte Test-App; Gerät für sichtbare UI-Tests entsperren:
./gradlew :app:connectedDebugAndroidTest -PpaperViewDeviceTests=true

# Realer HTTPS-Geräteablauf mit künstlichen Daten und Modell-/Quellen-Mocks:
# Benötigt openssl, genau ein autorisiertes ADB-Gerät und Backend-Abhängigkeiten.
python backend/tests/run_android_https.py

# Danach reguläre APK erneut erzeugen, da der Gerätetest die Test-App-ID baut:
./gradlew :app:assembleDebug
```

Das HTTPS-Skript erstellt temporäre Zertifikate und eine eigene temporäre Datenbank, startet/stoppt den Testserver, erstellt seine ADB-Weiterleitung und entfernt anschließend Zertifikate, Daten, Weiterleitung und beide Testpakete. Es installiert oder löscht niemals die persönliche App-ID.

## Backend für ein echtes Smartphone einrichten

1. Backend-Abhängigkeiten nach `backend/README.md` installieren. `PAPERVIEW_DATA_DIR` auf einen festen **absoluten**, privaten und dauerhaft gespeicherten Ordner setzen. Bei einem Neustart denselben Ordner verwenden; auf einem Server/Docker keine flüchtige Datenablage verwenden. Die SQLite-Datei heißt `paperview.sqlite3`.
2. Einen eigenen zufälligen `APP_TOKEN` privat konfigurieren und denselben Wert in Android als **Backend-Zugriffsschlüssel** eingeben. Weder in Git noch in Logs/öffentliche Shell-Ausgaben schreiben.
3. Backend ausschließlich an Loopback betreiben:

   ```sh
   cd backend
   .venv/bin/uvicorn paperview.main:app --host 127.0.0.1 --port 8000 --no-access-log --no-proxy-headers
   ```

4. HTTPS mit einem vom Smartphone vertrauten Zertifikat davor schalten. Beispiel für eine **eigene, tatsächlich auf diesen Server zeigende Domain**, nicht unverändert übernehmen:

   ```caddyfile
   deine-echte-domain.de {
       reverse_proxy 127.0.0.1:8000
   }
   ```

   DNS, Router/Firewall und Zertifikatsausstellung müssen passen. Nur die genauen Loopback-Adressen des eigenen Reverse Proxys als `TRUSTED_PROXIES=127.0.0.1,::1` freigeben. uvicorn weiterhin mit `--no-proxy-headers` betreiben; die Anwendung prüft den direkten Proxy-Peer selbst. Den internen Port 8000 nicht öffentlich freigeben. Caddy nicht mit Access-Logging sensibler Header konfigurieren. [Caddy Reverse Proxy](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy), [automatische Zertifikate](https://caddyserver.com/docs/automatic-https).
5. In Android `https://deine-echte-domain.de/`, Backend-Zugriffsschlüssel und einen **exakten**, für den eigenen Account verfügbaren Modellnamen speichern. OpenAI-Basis: `https://api.openai.com/v1`, serverseitige Allowlist: `LLM_ALLOWED_HOSTS=api.openai.com`. Eigenen OpenAI-Key in der App verschlüsselt speichern oder serverseitig `LLM_API_KEY` setzen. Der App-Key wird nur bei Modellaufrufen übertragen und vom Server nicht persistiert.
6. „Gespeicherte Verbindung testen“ ausführen. Zuerst müssen beide Backend-Schritte erfolgreich sein. Ein erreichbarer Server ersetzt weder gültige Modellberechtigungen noch API-Guthaben. Bei einem Fehler den betroffenen Schritt und die sichere Fehlermeldung verwenden.

Ohne eigenen HTTPS-Endpunkt ist die normale App nach Installation noch nicht mit einem dauerhaften Server verbunden. Der temporäre Testserver wird wieder beendet. Eine bloße LAN-IP mit HTTP oder eine selbstsignierte Zertifikatsdatei wird nicht als Produktionslösung freigeschaltet.

Für Betrieb unabhängig vom Entwicklungscomputer dieselbe Backend-Version auf einem dauerhaft laufenden VPS/Heimserver installieren, privaten persistenten Datenordner und HTTPS-Domain konfigurieren und uvicorn durch einen Dienstmanager (z. B. systemd) starten lassen. Zugriffe bleiben mit `APP_TOKEN` geschützt. Dies bleibt eine Einzelbenutzeranwendung. Es wurde in diesem Schritt kein öffentlicher Server bereitgestellt und keine Domain erfunden.

## APK installieren

Nach dem regulären `assembleDebug` liegt die korrigierte APK unter `app/build/outputs/apk/debug/app-debug.apk`.

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Damit eine gleich signierte vorhandene Installation aktualisieren; nicht vorher deinstallieren und keine App-Daten löschen. Bei einem Signaturkonflikt zunächst den bisherigen Signaturschlüssel klären. Nach dem Update Backend-URL, separaten Backend-Zugriffsschlüssel und Modellkonfiguration prüfen. Bestehende sicher gespeicherte Schlüssel bleiben erhalten. Keine Schlüssel in Fehlerberichte/Screenshots aufnehmen.
