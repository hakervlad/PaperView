> Frühere Geräteprüfungen auf Stand PR #5; sie ersetzen keine Geräteprüfung der neuen lokalen APK. Aktueller Stand: [LOCAL_ANDROID.md](LOCAL_ANDROID.md).

# Bugfix: API-Key-Speicherung und Tab-Navigation

## Ursachen

1. `PaperViewModel.configure` validierte die vollständige Backend-Konfiguration **vor** dem lokalen Schreiben. Ein OpenAI-Key allein konnte dadurch nicht gespeichert werden. Anschließend wurde nach einem erfolgreichen Schreiben `sync(false)` ausgeführt, dessen Netzwerkfehler die Bestätigung überschreiben konnte. Der gemeinsame `busy`-Schutz verwarf Speicheraktionen während laufender Aufrufe. Der übergebene leere Feldwert überschrieb vorhandene Schlüssel ohne expliziten Löschauftrag. Ein beschädigter Offline-Cache konnte zudem das Veröffentlichen bereits geladener Einstellungen verhindern.
2. `openTab` entfernte nur Detailseiten. Eine aufliegende Einstellungsseite wurde mit `popUpTo(...){saveState=true}` zusammen mit einem Haupt-Tab gespeichert. `restoreState=true` konnte diese Unterseite beim Wechsel zu „Mein Wissen“ erneut öffnen. Die Routen selbst waren verschieden; die Ursache war der gespeicherte Backstack.

## Korrekturen

- Lokale `SettingsPersistence` ohne Netzwerkanbindung; bestehender Android-Keystore-/AES-GCM-/DataStore-Speicher bleibt erhalten. Ein leerer Entwurf bewahrt den bisherigen Schlüssel; nur der ausdrückliche Löschbutton darf ihn löschen.
- Speicherzustand getrennt vom Netzwerk-`busy`-Zustand. Laufende Datenanfragen werden vor dem Speichern abgebrochen und abgeschlossen, damit kein alter Backend-Stand den Cache neu füllt. Keine automatische Verbindungsprüfung beim Speichern; der vorhandene Testbutton bleibt die explizite Prüfung.
- Bestätigung erst nach abgeschlossenem Schreiben; sichere Lade-/Schreibfehler werden sichtbar gemeldet. Geladene Einstellungen werden unabhängig vom Offline-Cache in den StateFlow übernommen.
- Schlüsselfeld wird nicht mit dem gespeicherten Geheimnis vorbefüllt. Es zeigt bei vorhandenem Schlüssel „API-Key gespeichert“. Eingaben bleiben passwortmaskiert und werden nicht in SavedInstanceState übernommen. Keine neuen Klartextspeicher oder Logs.
- Unterseiten werden vor dem Sichern/Wiederherstellen eines Haupt-Tabs entfernt. Gewöhnliches Zurück aus den Einstellungen kehrt weiterhin zum tatsächlichen Ursprung zurück.
- Keine Backend-API-, Theme- oder Layout-Neuentwicklung.

## Nachweis

Die beiden initialen Regressionstests wurden vor dem Fix auf einem per ADB verbundenen Android-16-Smartphone ausgeführt: **2 Tests, 2 Fehler**. Der lokale Speichertest lief in sein Timeout; der Navigationstest fand die erwartete Wissenssammlung nicht.

Nach dem Fix: **9 Gerätetests, 0 Fehler**:

- Schlüsselfeld ausfüllen/speichern ohne Backend-Konfiguration, Einstellungen verlassen und wieder öffnen.
- Tatsächlicher Android-Keystore-/DataStore-Roundtrip nach Neuinitialisierung; der Testschlüssel steht nicht im Klartext in der DataStore-Datei. Fehlgeschlagener optionaler Verbindungstest entfernt ihn nicht.
- Simulierter Schreibfehler wird im UI sichtbar; bisheriger Schlüssel bleibt verfügbar.
- Speichern wird während eines laufenden initialen Lesevorgangs nicht verworfen.
- Verzögert geladene Einstellungen löschen keinen bereits im maskierten Feld eingegebenen Schlüssel.
- Wiederholte Navigation zwischen Entdecken, Wissen und Einstellungen; SavedState-Wiederherstellung.
- System-Zurück aus Einstellungen zum jeweiligen Ursprung.
- Tatsächliche MainActivity-Neuerstellung aus Einstellungen und Wissen, anschließend korrekte Navigation.
- Ein zusätzlich nachgebildeter, von der alten Version gespeicherter Backstack darf Einstellungen nicht als Wissens-Tab öffnen.

**14 Unit-Tests bestanden**: 9 bestehende Sicherheits-/Mappingtests plus 5 neue Persistenztests (Speichern/Neuladen, leerer Entwurf, Ersetzen, ausdrückliches Löschen, Schreibfehler). Der vorhandene opt-in Backend-HTTP-Test wurde ohne seinen Testserver ausdrücklich übersprungen; er ist für diese lokalen Fehler nicht erforderlich. Debug-Build und Android Lint erfolgreich, keine Lint-Fehler. Bestehende nicht blockierende Bibliotheks-/Tooling-Hinweise bleiben.

## Reproduktion der Tests

```sh
# Instrumentierung nur in einer getrennten App-ID; die persönliche Installation bleibt unberührt.
./gradlew :app:connectedDebugAndroidTest :app:testDebugUnitTest :app:lintDebug -PpaperViewDeviceTests=true
# Reguläre Debug-APK ohne Test-Suffix:
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Die Test-Property installiert `com.example.aiknowledgefeed.regression`, nicht die vorhandene App. Tests verwenden ausschließlich künstliche Schlüssel ohne Anbieterzugriff. Die beiden separaten Testpakete wurden nach der Prüfung entfernt. Der bestehende Schlüssel in einer bereits installierten persönlichen App wurde weder ausgelesen noch geändert. Ein zuvor tatsächlich verworfener Schlüssel muss nach Installation des Fixes erneut eingegeben werden. Die Gültigkeit eines echten OpenAI-Schlüssels wurde nicht geprüft und ist nicht Voraussetzung für die lokale Speicherung.
