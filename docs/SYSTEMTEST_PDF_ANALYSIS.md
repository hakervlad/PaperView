# PaperView: PDF-Analyse und Systemtest

Stand: 9. November 2026, Branch `feat/persistent-pdf-analysis`.

PaperView arbeitet weiterhin ohne FastAPI, PC-Adresse, VPS oder lokalen HTTP-Server. Profil, Original-PDFs, Textabschnitte, Wissensbelege und Feeds liegen in der Android-App. Externe Modellaufrufe und wissenschaftliche APIs benötigen Internet. Das vorhandene Design und die Navigation bleiben erhalten; ergänzt wurden Statusmeldungen und die notwendige Zustimmung.

## Was implementiert wurde

- **Persistente Verarbeitung:** Room-Schema 2 ergänzt `analysis_tasks` mit Dokument-ID, Importzeit, Zustand, Zustimmung, Modell, Analyseversion, Sperrfrist und Fehlermeldung. Der Inhalts-Hash steht am Dokument; Seiten/Textabschnitte und Konzeptbelege bleiben getrennt gespeichert. Migration 1 → 2 erhält bestehende Profile und Dokumente. Ein unbekanntes historisches Importdatum wird mit 0 markiert und nicht erfunden.
- **Lokaler Import:** SAF liest maximal 10 MB. Das Original wird atomar im privaten `noBackupFilesDir/pdfs/` gespeichert. Ein eindeutiger SHA-256-Hash verhindert doppelte Importe. Ein WorkManager-Auftrag extrahiert lokal den Text; dafür sind weder Schlüssel noch Netz erforderlich. Nach einem Prozessende kann der gespeicherte Auftrag weiterarbeiten.
- **Externe Analyse:** Ein eigener, eindeutiger WorkManager-Auftrag je Dokument wartet auf Internet. Die Room-Aufgabe ist die maßgebliche Zustandsquelle. WorkData enthält nur die Dokument-ID. Neuer Schlüssel, Modell, ausdrückliche Erlaubnis der privaten Direktanbindung und Zustimmung je Dokument sind erforderlich. Späteres Speichern der Einstellungen setzt berechtigte ausstehende Analysen automatisch fort.
- **Zustände:** Importiert → Text extrahiert → wartet auf Schlüssel/Zustimmung/Analyse → wird analysiert → wartet auf Bestätigung → verarbeitet. Fehler werden gespeichert und angezeigt. Ein abgeschlossener, zur Analyseversion passender Vorgang wird nicht beim App-Start erneut analysiert. Inhaltsänderung, ausdrückliche Neuanalyse oder erforderliche Analyseversion erlauben erneute Verarbeitung.
- **Begrenzungen:** Eindeutige Arbeit, transaktionaler Claim, Laufzeitsperre und Cache verhindern reguläre Doppelverarbeitung. Bei temporären Anbieterfehlern höchstens zwei Wiederholungen mit exponentiellem Abstand; dauerhafte Fehler warten auf ausdrücklichen Versuch oder geänderte Konfiguration. Wird der Prozess nach einer erfolgreichen Anbieterantwort und vor deren Speicherung beendet, ist wie bei anderen HTTP-Clients kein absolutes Exactly-once-Versprechen für die Abrechnung möglich.
- **Wissensstatus:** Extrahierte Begriffe sind Kandidaten. Nur bestätigte Konzepte gelangen in das Retrieval. Seitenbelege und gerichtete Verknüpfungen werden lokal gespeichert. Relationsbezeichnungen wie „verwendet“ sind derzeit keine eigenen Graph-Kantentypen.
- **Schlüsselvorfall:** Vor dem Update gespeicherte Schlüssel bleiben verschlüsselt erhalten, sind für neue Modellaufrufe aber gesperrt. Nur ein tatsächlich anderer, neu eingegebener Schlüssel wird freigegeben. Der Vergleich erfolgt mit der sicheren Speicherung statt mit möglicherweise noch ungeladenem UI-State. Leere Eingaben erhalten einen vorhandenen Schlüssel; ausdrückliches Löschen setzt auch die Freigabe zurück. Die App kann einen Schlüssel nicht beim Anbieter widerrufen: Der offengelegte Schlüssel muss dort weiterhin widerrufen werden.

## Synthetische Test-PDF

Datei: [`app/src/test/resources/pdfs/neural-transformer.pdf`](../app/src/test/resources/pdfs/neural-transformer.pdf), drei Seiten, vollständig synthetisch, ohne persönliche Daten oder vorgetäuschte Forschungsliteratur. Die Android-Testkopie liegt unter `app/src/androidTest/assets/pdfs/`.

[`expected.json`](../app/src/test/resources/pdfs/expected.json) enthält sechs erwartete Begriffe mit Seitenzuordnung: Machine Learning, neuronale Netze, Deep Learning, Attention-Mechanismus, Transformer und Large Language Models. Erwartete gerichtete Beziehungen: Transformer → Attention; Large Language Models → Transformer. Das Dokument dient dem Funktionstest, nicht als Forschungsnachweis.

## Echte Modelltests: Budget vollständig verbraucht

Ein ausschließlich über die geschützte lokale Eingabe neu bereitgestellter Schlüssel wurde verwendet. Kein Schlüssel wurde in Quellcode, Git, Testdateien, Bericht oder APK eingebettet. Der erste unbrauchbare Eingabeversuch führte zu keiner HTTP-Anfrage. Insgesamt wurden **genau fünf kostenpflichtige Aufrufe** mit dem tatsächlich verfügbaren Modell **`gpt-6-luna`** ausgeführt; keine weiteren Aufrufe oder bezahlten Wiederholungen.

| Aufruf | Ergebnis |
|---|---|
| 1: kleine Verbindungsprüfung | Echte HTTPS-/Modellantwort, strukturiertes `ok=true` korrekt gelesen. |
| 2: synthetische PDF analysieren | 15 Konzepte extrahiert; alle sechs erwarteten Begriffe und beide gerichteten Beziehungen nachträglich in den gespeicherten Ergebnissen geprüft. Alle 15 zunächst `candidate`. |
| 3: Erklärung mit bestätigtem Vorwissen | Verwendet belegte Konzepte Machine Learning, Deep Learning und Transformer und erklärt deren Bezug zum Paper. |
| 4: dasselbe Paper ohne Vorwissen | Leere Vorwissensliste und leere Wissensverbindung; kein erfundenes bekanntes Konzept. Erklärung unterscheidet sich. |
| 5: weitere Erklärung mit neu gespeichertem Wissen | Neu als verstanden gespeicherter Wissenseintrag wurde im Kontext und Ergebnis berücksichtigt. Der Ergebnisbeleg verweist jedoch erneut auf dieselbe Originalquelle. Dies beweist **keinen** Live-Folgetag mit einem zweiten unterschiedlichen Paper. |

Die Live-Aufrufe liefen mit den tatsächlichen Kotlin-Services unter Robolectric auf dem Entwicklungsrechner. Sie waren **keine Live-Aufrufe auf dem Smartphone**. Öffentliche Quelle des Profilvergleichs: [arXiv 2611.08487](https://arxiv.org/abs/2611.08487), abstractbasiert. Die Erklärungen wurden auf Kontext-/Quellenzuordnung geprüft; eine vollständige fachwissenschaftliche Begutachtung ist damit nicht verbunden.

Die Anfängerantwort enthielt zu viel unerklärten Fachjargon. Deshalb verlangt der Prompt nun kurze zusätzliche Grundlagen; deren validierte Darstellung im bestehenden Detailtext ist mit Mock-Antworten getestet. Diese Promptänderung wurde wegen des ausgeschöpften Budgets **nicht nochmals live bewertet**. Ebenso wurde der opt-in Live-Test für den Folgetag verschärft: zuverlässige Paperkennungen müssen unterschiedlich sein, Tag-1-Paper werden als gesehen gespeichert und das neue Wissen muss im Folgetagsergebnis vorkommen. Dieser verschärfte Live-Test bleibt offen.

`PaidSystemTest` wird in normalen Builds übersprungen. Eine erneute Ausführung erfordert erneute ausdrückliche Kostenfreigabe, geschützte neue Schlüsselkonfiguration und einen expliziten, persistenten Budgetzähler. Der aktuelle Zähler steht auf 5/5 und darf nicht zurückgesetzt werden, um die Freigabe zu umgehen.

## Automatisierte Prüfungen

| Prüfung | Nachweis / Ergebnis |
|---|---|
| JVM / Robolectric / HTTPS-Mockserver | **42 bestanden**, 0 Fehler; drei zusätzliche Live-Proben im Standardlauf bewusst übersprungen (45 insgesamt). |
| Smartphone A142 / Android 16 | Standardlauf: **16 reguläre Tests bestanden**, 0 Fehler; fünf explizite Mehrphasen-/Live-Prüfungen zunächst übersprungen (Runner: 21 Tests). Echter SAF-Picker, drei PDF-Seiten, private Originaldatei, Kandidatenbestätigung, sichere Schlüsselablage, Navigation einschließlich Wiederherstellung und „Verstanden“-Button geprüft. |
| Separater App-Prozessneustart | **1 bestanden**: sechs bestätigte Konzepte, alle drei Textseiten und das Original nach `am force-stop` und neuem Prozess vorhanden. Kein Telefon-Werksreset oder vollständiger Geräteneustart. |
| Unterbrochener Import / WorkManager | **2 Phasen bestanden**: lokalen Auftrag persistent mit Verzögerung vorgemerkt, Prozess extern beendet, neue App-Instanz gestartet; WorkManager hat anschließend alle drei Seiten ohne Schlüssel extrahiert. |
| Daily Feed / neue Wissenseinträge | Geräte-UI-Test und JVM-Integration mit Mock-Quelle/Mock-Modell: Speichern bestätigt, lokale Sammlung aktualisiert, erneute Datenbankverbindung liest den Eintrag; am selben Tag keine weitere Anfrage, an Tag 2 anderes Paper und neues Vorwissen im Modellkontext/Ergebnis. |
| Externe Paperquellen | OpenAlex hat auf dem Smartphone echte HTTPS-Metadaten geliefert. arXiv war während der Live-Modellprobe auf dem Rechner erreichbar; auf dem Smartphone zunächst HTTP 429, bei einem späteren einzelnen Versuch nicht erreichbar / Timeout. Diese arXiv-Geräteproben sind **fehlgeschlagen**, nicht als bestanden gezählt. Eine separate Rechnerprobe wurde zunächst von OpenAlex-HTTP-429 wegen erhöhter Last blockiert. |
| Fehlerbehandlung | Mockserver prüfen ungültige Schlüssel, 403, Guthaben-/Rate-Limit-429, 5xx, Modellfehler, ungültiges JSON, Timeout und TLS-Abweisung. Fehlendes lokales PDF-Original meldet einen Speicherfehler, keinen Netzwerkfehler. |
| Sicherheits-Mutationstest | Umgekehrte Zustimmungsbedingung verursachte **zwei Fehlschläge**; anschließend korrekte Bedingung wiederhergestellt und grüne Gesamtsuite ausgeführt. |
| Python-Referenz und Datenexport | **42 bestanden**; keine FastAPI-Nutzung durch Android. |
| Android Lint | **0 Fehler, 30 Warnungen, 2 Hinweise**. Unter anderem veraltete Abhängigkeiten/Tooling und drei bekannte `TrustAllX509TrustManager`-Meldungen im transitiven BouncyCastle-PDF-JAR. App-TLS-Prüfungen bleiben aktiv und negative Zertifikat-/Hostname-Tests bestehen. Nicht warnungsfrei. |
| Debug-APK | Normale App-ID, Version 1.2-analysis / Code 3; APK-Signatur gültig; normale Update-Installation auf dem Smartphone erfolgreich, ohne dessen persönliche App-Daten zu löschen. |

Die Befehlsausgaben liegen unter `app/build/reports/systemtest/` (lokale, nicht versionierte Artefakte). Die öffentliche Quellenprüfung ist getrennt von der deterministischen Testsuite, damit ein echter Anbieterfehler sichtbar bleibt und nicht die Prüfung einer anderen Quelle verhindert.

Abgedeckt sind lokale PDF-Extraktion und Fehlergrenzen, eindeutige Hashes, Kandidaten/Bestätigung, Wissensverbindungen/Belege, Room-Neuöffnung und Migration, ausstehende Analyse ohne Schlüssel/Zustimmung, parallele Analyse, Fehlerzustände und kontrollierter erneuter Versuch, gelöschte Dokumente, TLS-/HTTP-Fehler, Modellantwortvalidierung, Tagescache und parallele Feed-Claims. Tag 2 wird über eine injizierte Uhr simuliert; die echte Telefonzeit wird nicht verändert.

Der vollständige lokale PDF-zu-Feed-Integrationstest verwendet ausdrücklich ein Mock-Modell und eine Mock-Paperquelle. Er prüft, dass als verstanden gespeichertes Wissen nach Datenbank-Neuöffnung bei einer **anderen** Tagesveröffentlichung im Retrieval und Modellkontext vorkommt. Mock-Ergebnisse werden nicht als Live-Personalisierung ausgewiesen.

## Behobene Fehler und Grenzen

- Fehlende dauerhafte PDF-Aufgaben und fehlendes privates PDF-Original wurden ergänzt.
- Ein Analysefehler verschwindet nicht stillschweigend im UI-State; Wiederholungen sind begrenzt.
- Beim Löschen werden Extraktions- und Analyseauftrag beendet; späte Extraktion darf das Dokument nicht neu anlegen.
- Ein fehlendes privates PDF-Original wird jetzt als lokaler Speicherfehler angezeigt und dauerhaft am Auftrag festgehalten.
- Historische Navigationstests suchten die inzwischen entfernte Überschrift „Backend-Verbindung“. Sie prüfen jetzt die lokale Einstellungsseite.
- Die Android-Dateiauswahl kann beim Wechsel der Wurzel veraltete UI-Automator-Objekte liefern. Der Test löst den Dateiselektor erst beim Klick auf und berücksichtigt deutsche/englische Navigation.
- Der UI-Test muss den Link zur Wissenssammlung vor dem Klick vollständig sichtbar scrollen. Danach ist die vertikale LazyColumn gezielt von der horizontalen Kategorienleiste zu unterscheiden. Die korrigierte Prüfung bestätigt sowohl Navigation als auch die gespeicherte Card; die Testfehler wurden nicht durch geändertes Produktdesign umgangen.
- Kein OCR. Grenzen: 100 Seiten, 120.000 Zeichen, 60 Textabschnitte; für KI maximal zwölf Abschnitte à 2000 Zeichen. Große Arbeiten werden damit ausdrücklich **nicht vollständig** extern analysiert. PDFBox-Parsing hat kooperative Abbruchprüfungen und ist keine isolierte Parser-Sandbox.
- Lexikalisches Retrieval kann insbesondere sprachübergreifende Bezüge übersehen. ID- und Schema-Prüfung beweisen nicht die fachliche Richtigkeit jeder Modellaussage. Grundlagenqualität nach der Promptkorrektur bleibt live unbestätigt.
- Kein Neustart des gesamten privaten Smartphones, kein Abschalten seiner Funkverbindungen und kein vollständiger einzelner Live-Durchlauf vom Picker bis zum Feed auf dem Telefon. Prozessneustart, gespeicherte lokale Daten und kontrollierte Netzwerkfehler werden getrennt geprüft. Die zuvor funktionierende private Installation wurde nicht für Tests gelöscht.
- Direkte mobile BYOK-Anbindung bleibt eine bewusste private Testentscheidung. Keystore schützt die Ablage, nicht gegen kompromittierte Geräte beim Gebrauch. Für Veröffentlichung ist ein abgesicherter Gateway-Adapter erforderlich; lokale Funktionen bleiben davon unabhängig. [OpenAI-Schlüsselrichtlinien](https://help.openai.com/en/articles/5112595-best-practices-for-api-key-safety).

## Installation und weitere Nutzung

Die neue normale Debug-APK liegt unter `app/build/outputs/apk/debug/PaperView-1.2-analysis-debug.apk`, Paket `com.example.aiknowledgefeed`, Version 1.2-analysis, Code 3. Die Signaturprüfung war erfolgreich. Die normale APK wurde auf dem verbundenen Smartphone erfolgreich als Update installiert; die vorhandene persönliche Datenablage wurde nicht geleert. SHA-256: `639e8730032578dbb3629b321d6c1c3139f150cb4b529cf4f99930301bc16ad4`.

Als **Update** installieren:

```sh
adb install -r app/build/outputs/apk/debug/PaperView-1.2-analysis-debug.apk
```

Nicht zuvor deinstallieren und keine App-Daten löschen. Bei einer abweichenden Signatur den bisherigen Signierschlüssel klären. Die Geräteprüfungen verwenden eine getrennte Paket-ID `.regression` und ausschließlich synthetische Inhalte/Testschlüssel.

Den offengelegten Schlüssel beim Anbieter widerrufen. Einen neu erzeugten Schlüssel ausschließlich außerhalb des Chats in der App eingeben, als Modell `gpt-6-luna` wählen und direkte private Modellanbindung bewusst erlauben. Die OpenAI-Basis-URL darf leer bleiben. Ausstehende Dokumente zeigen ihren Status; je Dokument die Übertragung benötigter Textabschnitte prüfen und erlauben. Danach Kandidaten bestätigen. Eigene anschließende Nutzung, Verbindungstests und Feeds verursachen zusätzliche Modellaufrufe und gehören nicht zu den bereits verbrauchten fünf Testaufrufen.

Für die lokale App wird **kein Backend-Server** gestartet. Öffentliche Paperquellen können unabhängig von der App ausfallen oder Rate Limits melden. [OpenAlex-Authentifizierung und Limits](https://help.openalex.org/api/authentication/), [arXiv-API](https://info.arxiv.org/help/api/user-manual.html).

## Reproduzierbare kostenfreie Prüfungen

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
PYTHONPATH=backend python -m pytest backend/tests -q

# Isolierte Test-App bauen und auf einem entsperrten Gerät installieren:
./gradlew assembleDebug assembleDebugAndroidTest -PpaperViewDeviceTests=true
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb push app/src/test/resources/pdfs/neural-transformer.pdf /sdcard/Download/PaperView-synthetic.pdf
adb shell am instrument -w com.example.aiknowledgefeed.regression.test/androidx.test.runner.AndroidJUnitRunner
```

Der SAF-Test erwartet ausschließlich synthetische Testdaten in der isolierten App. Vor einem wiederholten vollständigen Lauf kann ausschließlich deren Datenablage (`com.example.aiknowledgefeed.regression`) zurückgesetzt werden; niemals die persönliche App. Mehrphasenprüfungen sind opt-in: `DevicePersistenceCheckTest` benötigt `verify_restart=true`, `PendingPdfRestartTest` zuerst `pending_phase=stage`, danach externen Force-stop und `pending_phase=verify`. `PublicSourcesDeviceTest` benötigt `public_sources=true`; diese Proben kontaktieren echte öffentliche Dienste und können wegen deren Verfügbarkeit scheitern. Anschließend wieder eine normale APK ohne Testparameter bauen.

Issue #1 bleibt wegen nicht vollständig nachgewiesener Live-/Geräteakzeptanzkriterien offen. Es wird kein erfolgreicher vollständiger Smartphone-Live-Durchlauf behauptet.
