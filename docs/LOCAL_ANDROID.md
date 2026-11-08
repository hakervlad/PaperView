# PaperView ohne eigenen Server

Die Anwendung läuft innerhalb der Android-App: Compose → ViewModel/StateFlow → lokale Repositories und Kotlin-Services → Room/SQLite. Ein PC, VPS, FastAPI-Prozess oder lokaler HTTP-Server ist für lokale Funktionen nicht erforderlich. Nur wissenschaftliche APIs und Modellanfragen benötigen Internet.

## Daten und Upgrade

Room liegt im privaten, vom Android-Backup ausgeschlossenen App-Verzeichnis. Tabellen enthalten Profil, Dokumentstatus, Textabschnitte mit Seitenreferenz, Konzepte, Belege, Konzeptverbindungen, Papers, Tagesausgaben, bereits angezeigte Kennungen und Migrations-/Retry-Metadaten. Secrets liegen ausschließlich in der bestehenden Keystore-/AES-GCM-geschützten Einstellungsspeicherung. Keine destruktive Room-Migration.

Der verschlüsselte Alt-Cache wird beim ersten Start einmalig und transaktional übernommen. Er wird nicht gelöscht. Bestehende API-Keys bleiben erhalten. Ein Cache enthält nur den zuletzt geladenen Stand, nicht zwangsläufig sämtliche Serverdaten oder PDF-Textabschnitte.

Falls alte Serverdaten existieren:

```sh
python3 backend/export_local.py /absoluter/pfad/paperview.sqlite3 /privater/pfad/paperview-archiv.json
```

Der Export öffnet die originale Datenbank nur lesend und schreibt eine neue Datei mit Modus 0600; vorhandene Dateien werden nicht überschrieben. Profil, Konzepte, Dokumenttexte/Belege, Papers und Feed-Historie werden exportiert; Schlüssel/Einstellungen nicht. Das Archiv enthält persönliche Inhalte: privat aufbewahren, über USB oder eine vertrauenswürdige verschlüsselte Verbindung übertragen und niemals in Git ablegen. Anschließend in den App-Einstellungen als alte Serverdaten über die Android-Dateiauswahl importieren. Lokale vorhandene Einträge werden nicht überschrieben, doppelte Namen/IDs zusammengeführt und Imports nur vollständig übernommen. Die alte Datenbank bleibt als Rückfallquelle erhalten.

An den üblichen Datenpfaden auf dem Entwicklungsrechner wurde während der Umstellung keine produktive Serverdatenbank gefunden. Nicht erreichbare andere Server wurden nicht als leer angenommen und nicht gelöscht.

## Private direkte Modellanbindung: bewusste Einschränkung

OpenAI rät davon ab, API-Schlüssel in mobilen Clientumgebungen einzusetzen. Ein eingebetteter Betreiber-Key wäre aus einer APK extrahierbar. Auch ein vom Benutzer eingegebener, im Keystore verschlüsselt gespeicherter Key kann auf einem kompromittierten/rooteten Gerät oder durch Runtime-Instrumentierung beim Gebrauch entwendet werden. Der Keystore macht einen mobilen Client nicht zu einem sicheren öffentlichen API-Gateway. [Offizielle OpenAI-Empfehlungen](https://help.openai.com/en/articles/5112595-best-practices-for-api-key-safety).

Für diese **private Testversion** ist ausschließlich ein eigener vom Benutzer eingegebener Schlüssel vorgesehen. Direkte Anbindung ausdrücklich in den Einstellungen erlauben; kein Key wird mitgeliefert oder in die APK eingebettet. Modellname und HTTPS-Endpunkt bewusst wählen. TLS-/Hostnamenprüfung bleiben aktiv, Redirects sind deaktiviert, Schlüssel und Modell-/Dokumentinhalte werden nicht protokolliert. Bei Zweifeln Schlüssel beim Anbieter widerrufen, mit begrenzten Rechten und Kostenlimit arbeiten. Ohne Internet gibt es keinen externen KI-Aufruf.

Für eine öffentliche Veröffentlichung muss `ModelClient` durch einen authentifizierten, serverseitig abgesicherten Gateway-Adapter ersetzt werden; keine gemeinsame Betreiber-API-Credential in den Client verlagern. Ein solcher Produktionsgateway ist noch nicht implementiert. Wissenssammlung, PDF-Extraktion und Room bleiben davon unabhängig.

Die PDF-Analyse verlangt zusätzlich eine Zustimmung je Dokument. Nur maximal zwölf Textabschnitte à 2000 Zeichen werden extern gesendet, nicht die PDF-Datei oder das vollständige Wissensarchiv. Dokumenterwähnungen erzeugen Kandidaten, die erst nach Nutzerbestätigung als Vorwissen gelten.

## Externe Quellen und Personalisierung

OpenAlex und arXiv werden direkt über HTTPS abgefragt. Casual OpenAlex-Abfragen funktionieren laut aktueller Dokumentation ohne Key; Authentifizierung kann für höhere Budgets/weitere Nutzung nötig werden. arXiv-Aufrufe werden serialisiert und mit mindestens drei Sekunden Abstand ausgeführt. Zeitfenster beziehen sich auf das Veröffentlichungsdatum. DOI/arXiv-IDs und bereits angezeigte Kennungen verhindern Wiederholungen. [OpenAlex](https://help.openalex.org/api/authentication/), [arXiv](https://info.arxiv.org/help/api/user-manual.html).

Retrieval verwendet nachvollziehbaren Begriffsoverlap und direkte Verknüpfungen ausschließlich bestätigter Konzepte. Das Modell erhält ein Abstract und wenige passende Wissenseinträge. Zurückgegebene Konzept-IDs müssen aus diesem Kontext stammen. Referenzierte Konzepte verlangen einen ausgefüllten Verbindungsabschnitt; ohne referenziertes Wissen muss dieser leer bleiben. Ohne sinnvollen Bezug wird keine Verbindung erfunden. Quellen-URL und Veröffentlichungsdatum stammen aus den Metadaten; alle Erklärungen sind als abstractbasiert gekennzeichnet. Eine strukturelle Prüfung ersetzt keine wissenschaftliche Faktenprüfung.

Tagesausgaben werden anhand des lokalen Kalendertags gespeichert und wiederverwendet. Gleichzeitige Generierungen beanspruchen eine persistente lokale Sperre; Fehler haben eine Wartezeit und Tagesversuchsgrenze. Der letzte erfolgreiche Feed bleibt offline verfügbar. Neues bestätigtes Wissen beeinflusst zukünftige Ausgaben, nicht rückwirkend jede gespeicherte Erklärung. Ältere Einträge in „Mein Wissen“ öffnen ihre gespeicherte personalisierte Detailerklärung aus der lokalen Feed-Historie; ein bloßes Abstract dient nur als Rückfall, wenn keine solche Erklärung vorhanden ist.

## Einrichtung der privaten Testversion

1. Die normale Debug-APK als Update installieren, nicht vorher deinstallieren. Die Paket-ID bleibt `com.example.aiknowledgefeed`; Version `1.1-local`, Versionscode 2.
2. Unter **Einstellungen → Persönliches Wissen** Studienfach, Schwerpunkte/Interessen und ausdrücklich bekanntes Vorwissen speichern. Das funktioniert ohne API-Key und ohne Internet.
3. **PDF auswählen → Lokal importieren und Text extrahieren**. Es gibt keinen Upload an einen eigenen Server. Ein Dokument aus dem Alt-Cache mit fehlenden Textabschnitten ist entsprechend markiert; vollständiges Serverarchiv importieren oder PDF erneut lokal auswählen.
4. Unter **KI-Modell konfigurieren** einen eigenen OpenAI-Key und einen exakten, für das eigene Konto verfügbaren Chat-Completions-/JSON-kompatiblen Modellnamen eintragen. Die Basis-URL kann für OpenAI leer bleiben (`https://api.openai.com/v1`). Keine Backend-URL und kein Backend-Zugriffsschlüssel sind nötig.
5. Die Risiken der direkten privaten Anbindung lesen und die Checkbox ausdrücklich aktivieren. **KI-Einstellungen speichern** speichert lokal; es bestätigt noch keine Modellverbindung. Leeres Schlüsselfeld bewahrt einen vorhandenen Key, Löschen erfolgt nur über den Löschbutton.
6. **Gespeicherte Verbindung testen** prüft lokale Datenbank, Anbieter und ein echtes Modellresultat. Ungültiger Schlüssel, Guthabenmangel, Rate Limit, Modell-/Parameterfehler, TLS- und Netzwerkfehler werden getrennt angezeigt. Ein fehlgeschlagener Test löscht den Schlüssel nicht.
7. Bei einem lokalen Dokument **Konzepte mit KI ermitteln** und erst nach Prüfung der Übertragung zustimmen. Kandidaten korrigieren und **Als verstanden bestätigen**. Anschließend **Feed aktualisieren** oder die App am nächsten Tag erneut öffnen.
8. In der Detailansicht ein Thema als verstanden markieren. **Mein Wissen** zeigt dauerhaft gespeicherte Konzepte und Paperbezüge. Neue bestätigte Einträge werden bei späteren Ausgaben berücksichtigt.

Für einen anderen kompatiblen Anbieter ist dessen dokumentierte HTTPS-Basis-URL erforderlich. Der frei eingegebene Anbietername ist nur eine Bezeichnung und ändert den Endpunkt nicht automatisch. Es gibt keine serverseitigen Modell-/Schlüsselvorgaben mehr.

## Bauen und prüfen

Hier mit JDK 21 und Android SDK 35 geprüft. JAVA_HOME/ANDROID_HOME beziehungsweise Android-Studio-Konfiguration passend setzen.

```sh
# Normale App, kein FastAPI-Server nötig:
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug

# Optionaler Live-Test nur öffentlicher Metadaten mit den tatsächlichen Kotlin-Clients:
PAPERVIEW_LIVE_SOURCES=1 ./gradlew :app:testDebugUnitTest --tests '*LiveScientificSourcesTest'

# Auf einem später wieder verbundenen, entsperrten Gerät; getrennte Test-App-ID:
./gradlew :app:connectedDebugAndroidTest -PpaperViewDeviceTests=true

# Python-Referenz und nur lesenden Alt-Datenexport prüfen:
PYTHONPATH=backend python -m pytest backend/tests -q
```

Auf diesem speicherbegrenzten Entwicklungsrechner wurden Gradle-Aufrufe nacheinander mit `--max-workers=1 -Dorg.gradle.jvmargs='-Xmx900m -Dfile.encoding=UTF-8' -Pkotlin.compiler.execution.strategy=in-process` ausgeführt. Erste Builds waren durch abgestürzte Gradle-Daemons unter Ressourcenlast unterbrochen; diese Unterbrechungen wurden nicht als erfolgreiche Prüfungen gewertet.

Der Gerätetest-Parameter baut eine isolierte App-ID mit `.regression`. Danach eine normale APK ohne diesen Parameter erstellen. Für die Auslieferung liegt zusätzlich eine feste Kopie unter `app/build/outputs/apk/debug/PaperView-1.1-local-debug.apk`.

```sh
adb install -r app/build/outputs/apk/debug/PaperView-1.1-local-debug.apk
```

Auch die manuelle Installation der APK auf dem Smartphone ist möglich. Bei einem Signaturkonflikt den bisherigen Signaturschlüssel klären; nicht zur Problemlösung deinstallieren oder App-Daten löschen. APKs und Testberichte liegen unter dem von Git ausgeschlossenen Build-Verzeichnis.

## Prüfergebnisse und Grenzen

Abschließender Stand (8. November 2026):

- Normale Debug-APK und Instrumentierungs-APK erfolgreich erstellt; Paket-ID ohne Test-Suffix, Version 1.1-local.
- **32 JVM-Tests bestanden**, keine Fehler. Eine zusätzliche Live-Quellenprobe wird im Standardlauf ausdrücklich übersprungen.
- Diese Live-Probe wurde separat mit `PAPERVIEW_LIVE_SOURCES=1` ausgeführt: Die tatsächlich ausgelieferten Kotlin-HTTP-Clients haben über HTTPS **30 OpenAlex-Paper und 30 arXiv-Paper** geladen und geparst. Dies war auf dem Entwicklungsrechner, nicht auf dem derzeit getrennten Smartphone.
- **42 Python-Referenz-/Exporttests bestanden**. Sie werden für den Betrieb der App nicht benötigt.
- Android Lint: **0 Fehler**, 26 Warnungen und zwei Hinweise. Darunter sind drei `TrustAllX509TrustManager`-Meldungen im transitiven PDF-Kryptografie-JAR `org.bouncycastle:bcpkix-jdk15to18:1.72`. PaperViews Netzwerkclients bauen keinen eigenen TrustManager und keinen HostnameVerifier ein. Zusätzliche negative TLS-Tests belegen, dass nicht vertrauenswürdige Zertifikate bei Modell- und Paper-Clients sowie falsche Zertifikat-Hostnamen abgewiesen werden, bevor ein HTTP-Request mit Schlüssel gesendet wird. Die Bibliothekswarnungen wurden nicht unterdrückt; ihr Abhängigkeitsstand muss vor einer öffentlichen Veröffentlichung erneut geprüft werden. Weitere Meldungen betreffen unter anderem Abhängigkeiten/Tooling, Target-SDK und bestehende Projektkonfiguration. Lint wird nicht als warnungsfrei bezeichnet.
- Getestet: Room-Neuöffnung/Offline-Bestand, lokale PDF-Extraktion, leere/ungültige/zu große PDFs und Seitenlimits, Zustimmung/Kandidatenstatus, Wissen bestätigen/löschen, Duplikate, vollständiger lokaler PDF-zu-Feed-Ablauf, Folgetagswissen, tägliches Caching, parallele Claims, Fehlversuch/Abkühlung, alte Feeds erhalten, TLS-Mockantworten/401/403/429/5xx/Timeout/ungültiges JSON, sichere Schlüsselzusammenführung, Weiterarbeiten bei nicht lesbaren KI-Einstellungen und während wartender Feed-Anfrage.
- Upgrade-Tests: idempotente Cache-/Archivübernahme, lokale Inhalte nicht überschreiben, Textabschnitte/Seitenbelege/Feed-Historie erhalten, fehlende Dokumenttexte anzeigen und vollständige Paper-Metadaten nachträglich ergänzen. Ungültige Archive werden ohne Teilimport abgewiesen.
- Eine absichtlich umgekehrte Zustimmungsprüfung wurde durch **drei fehlschlagende Tests** erkannt; danach wurde die korrekte Bedingung wiederhergestellt und die normale Gesamtsuite erfolgreich ausgeführt.

Die neuen Android-Gerätetests sind kompiliert und bereit, wurden jedoch auf Wunsch des Benutzers **noch nicht ausgeführt**. Es wurde keine persönliche Smartphone-Installation ersetzt und keine persönliche Datenablage ausgelesen oder gelöscht. Issue #1 bleibt wegen offener Live-/Geräteakzeptanzkriterien offen.

Der Benutzer möchte die neuen Gerätetests später durchführen; die ADB-Verbindung ist getrennt. Frühere Gerätetests der Serverarchitektur belegen **nicht** die neue lokale Architektur. Ein Live-Modellaufruf mit dem privat auf dem Smartphone gespeicherten API-Key wurde nicht ausgeführt. Modelltests verwenden ausdrücklich Test-Clients/HTTPS-Mockserver; die öffentliche Paperquellen-Probe enthält keine persönliche Wissensbasis und keinen Modellaufruf.

Grenzen der ersten lokalen Version: keine OCR; maximal 10 MB, 100 Seiten, 120.000 extrahierte Zeichen und 60 Textabschnitte pro PDF. Die KI analysiert maximal die ersten zwölf Abschnitte; nicht das gesamte Dokument. Das PDF-Parsing erfolgt auf einem Hintergrundthread, das kooperative Zeitlimit wird zwischen Verarbeitungsschritten geprüft. Papererklärungen beruhen auf Abstracts. Lexikalisches Retrieval kann insbesondere bei unterschiedlichen Sprachen sinnvolle Bezüge übersehen. Struktur-/ID-Prüfung kann wissenschaftliche Behauptungen nicht inhaltlich verifizieren. Direkte Modellanbindung bleibt eine private Testentscheidung; ein öffentlicher Gateway ist noch nicht implementiert. Tagesfeeds werden beim Öffnen angestoßen, kein exakter täglicher Hintergrundalarm versprochen.
