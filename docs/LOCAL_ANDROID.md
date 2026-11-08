# PaperView ohne eigenen Server

Die Zielarchitektur läuft innerhalb der Android-App: Compose → ViewModel/StateFlow → lokale Repositories und Kotlin-Services → Room/SQLite. Ein PC, VPS, FastAPI-Prozess oder lokaler HTTP-Server ist für lokale Funktionen nicht erforderlich. Nur wissenschaftliche APIs und Modellanfragen benötigen Internet.

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

Retrieval verwendet nachvollziehbaren Begriffsoverlap und direkte Verknüpfungen ausschließlich bestätigter Konzepte. Das Modell erhält ein Abstract und wenige passende Wissenseinträge. Zurückgegebene Konzept-IDs müssen aus diesem Kontext stammen. Ohne sinnvollen Bezug wird keine Verbindung erfunden. Quellen-URL und Veröffentlichungsdatum stammen aus den Metadaten; alle Erklärungen sind als abstractbasiert gekennzeichnet. Eine strukturelle Prüfung ersetzt keine wissenschaftliche Faktenprüfung.

Tagesausgaben werden anhand des lokalen Kalendertags gespeichert und wiederverwendet. Gleichzeitige Generierungen beanspruchen eine persistente lokale Sperre; Fehler haben eine Wartezeit und Tagesversuchsgrenze. Der letzte erfolgreiche Feed bleibt offline verfügbar. Neues bestätigtes Wissen beeinflusst zukünftige Ausgaben, nicht rückwirkend jede gespeicherte Erklärung.

## Noch zu prüfender Stand

Die Umsetzung und Prüfungen laufen. Die ADB-Verbindung wurde getrennt; der Benutzer möchte die neuen Gerätetests später durchführen. Die früheren Geräteprüfungen der Serverarchitektur belegen **nicht** die neue lokale Architektur. Live-Modellaufruf ohne bereitgestellten privaten API-Key nicht nachgewiesen. Abschließende Testergebnisse und APK-Pfad werden nach Build/Tests ergänzt.
