# Eigenständige Android-Architektur – Implementierungsplan

**Ziel:** PaperView arbeitet für sämtliche lokalen Funktionen ohne PC, VPS oder HTTP-Server.

**Architektur:** Das bestehende einzelne `app`-Modul und MVVM-Frontend bleiben bestehen. Room ist die maßgebliche lokale Datenquelle. Kleine Kotlin-Services übernehmen PDF-Extraktion, bestätigtes Wissen/Retrieval, HTTPS-Quellen, austauschbare Modellanbindung und tägliche Feed-Generierung. Keystore-Einstellungen bleiben erhalten. FastAPI bleibt eine historische Referenz.

**Technologien:** Kotlin/Compose, Room mit KSP, Coroutines/StateFlow, OkHttp/Retrofit, PDFBox-Android, Android Storage Access Framework, Android Keystore.

## 1. Datenhaltung und Upgrade

- `backend/database/`: Room-Entities, DAO, Datenbank und exportiertes Schema für Profil, Dokumente, Textabschnitte, Wissenskonzepte/-verbindungen, Papers, tägliche Feeds, gesehene Kennungen und Metadaten.
- `backend/repositories/LocalKnowledgeRepository.kt`: transaktionale lokale Schreiboperationen, bestätigte Konzepte, idempotentes Lernen aus Feed-Beiträgen, lexical Retrieval nur bestätigten Wissens.
- Keine destruktiven Migrationen. Datenbank im privaten, vom Backup ausgeschlossenen App-Bereich; keine Secrets in Tabellen.
- Erst Tests für Profil/Begriff-Neuladen, Duplikate, unbestätigtes Wissen, Löschung und Datenbankneustart, dann Implementierung.

## 2. PDF lokal

- `backend/services/LocalPdfService.kt`: SAF-Stream begrenzt lesen; PDFBox-Android extrahiert Text und Seitenreferenzen lokal. Grenzen für Dateigröße, Seiten, Text und Verarbeitungsdauer; Fehler für leere, verschlüsselte, beschädigte und gescannte Dateien.
- Atomar Dokument und Textabschnitte speichern. Kein Netzwerk bei Import. KI-Analyse nur nach separater Zustimmung, mit begrenzten Auszügen; Kandidaten bleiben unbestätigt.
- Tests mit künstlicher PDF, leeren/beschädigten Dateien, Scan-PDF, Grenzen und fehlgeschlagener Modellanalyse.

## 3. Externe HTTPS-Dienste

- `backend/api/ScientificSources.kt`: OpenAlex und arXiv direkt, begrenzte Antworten, Veröffentlichungsdatum statt Indexdatum, DOI/arXiv-Deduplizierung, Rate Limit und Timeout. OpenAlex-Key optional geschützt konfigurierbar.
- `backend/services/ModelClient.kt`: austauschbares Interface für strukturierte Antworten. OpenAI-kompatibler HTTPS-Client ohne Redirects, Klartext-Logging oder TLS-Umgehung; Schlüssel ausschließlich aus sicherer Speicherung. Fehlermeldungen unterscheiden Authentifizierung, Guthaben, Limit, Modell, TLS, Netzwerk und ungültiges JSON.
- Private BYOK-Testversion ausdrücklich dokumentieren: Ein mobiles Gerät kann Schlüssel zur Laufzeit nicht gegen Root/Instrumentation schützen; für Veröffentlichung austauschbarer authentifizierter Gateway. Lokale Datenhaltung bleibt gatewayunabhängig.
- MockWebServer-Tests für Requests, Statuscodes und ungültige Antworten. Live-Erreichbarkeit von Paperquellen getrennt prüfen. Kein behaupteter Live-Modelltest ohne bereitgestellten Schlüssel.

## 4. Lokale Personalisierung und Tagesfeed

- `LocalPersonalizer.kt`: nur passende bestätigte Konzepte plus Paper-Abstract senden; zurückgegebene Konzept-IDs und Quellen validieren, keine unbelegten Verbindungen akzeptieren, Abstractbasis kennzeichnen.
- `LocalFeedService.kt`: lokaler Kalendertag, transaktionale Claim-/Retry-Metadaten, erfolgreiche Ausgaben wiederverwenden, begrenzte Modellaufrufe und Fehlerabkühlung, alte Ausgaben offline behalten. Neues verstandenes Wissen bei nächsten Ausgaben abrufen.
- Tests für Parallelität, Tageswechsel, Wiederverwendung, Fehlversuch, Offline-Bestand und zukünftigen Wissensbezug.

## 5. Screens und Datenmigration

- `PaperViewRepository.kt` und `PaperViewModel.kt`: lokale Repositories statt FastAPI-Client; Room-Änderungen über Flow/StateFlow sichtbar machen. Lokale Schreiboperationen unabhängig von Internet/KI.
- `SettingsScreen.kt`: Backend-URL/Token-Felder entfernen; vorhandene Material-Komponenten und Design beibehalten. Lokaler PDF-Import, Modellkonfiguration, Zustimmung und Status ergänzen. Keine neue Navigation.
- Verschlüsselten Alt-Cache einmalig idempotent nach Room übernehmen; alten Speicher nicht löschen. Bestehende API-Keys bleiben erhalten.
- `backend/export_local.py`: bestehende Server-SQLite nur lesend in private JSON-Datei exportieren, ohne Secrets; Alt-Dateien unverändert lassen.
- `LegacyArchiveImporter.kt`: begrenzte, validierte Datei über SAF atomar importieren; stabile IDs/Belege/Feeds erhalten, lokale Daten nicht überschreiben. Keine automatische Verbindung zu alten Serveradressen.

## 6. Prüfen und integrieren

- Unit-Tests, Room-/PDF-/Offline- und Compose-Gerätetests in isolierter App-ID, reale Quellenprobe; echte Modellanfrage nur bei gültigem privatem Schlüssel.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` und isolierte Instrumentierung.
- Keine vertraulichen Dokumente, keine Löschung persönlicher App-Daten, keine globale Skillinstallation.
- Ergebnisse/Einschränkungen und Upgrade-Anleitung dokumentieren, reguläre APK erzeugen, Branch/PR nach Prüfungen integrieren. Issue #1 bei offenen Live-Akzeptanzkriterien offen lassen.

Die Architektur ist durch die ausdrückliche Vorgabe des Nutzers festgelegt. Keine erneute Freigabe routinemäßiger Implementierungsentscheidungen erforderlich; Umsetzung durch Codex, keine DeepSeek-Worker.


## Umsetzungsstand

Lokale Room-/PDF-/Quellen-/Modell-/Feed-Komponenten und Frontend-Anbindung implementiert. Einmalige Cache-Übernahme und privater Serverarchiv-Import vorhanden. Aktueller Teststand, Grenzen und Installation: [LOCAL_ANDROID.md](../LOCAL_ANDROID.md). Neue Gerätetests wurden auf Wunsch vertagt; Live-KI ohne bereitgestellten privaten Testschlüssel nicht behauptet. Keine DeepSeek-Worker gestartet.
