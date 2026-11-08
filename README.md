# 🧠 PaperView

### Neue Forschung. Erklärt mit deinem Wissen.

**Android · Kotlin · Jetpack Compose · Room · Private Testversion**

### 📄 Importieren → 🧩 Verknüpfen → 💡 Verstehen

- **Dein Wissen als Ausgangspunkt:** PDFs lokal importieren, erkannte Konzepte prüfen und bewusst bestätigen.
- **Neue Forschung einordnen:** OpenAlex und arXiv direkt abfragen; deutsche Erklärungen verbinden Abstracts mit passendem, bestätigtem Vorwissen.
- **Wissen behalten:** Verstandene Themen, Quellen und Tagesausgaben dauerhaft auf dem Smartphone speichern.

### 📱 Eigenständig auf deinem Smartphone

Kein eigener Server, PC oder VPS erforderlich. Profil, PDF-Texte und Wissenssammlung funktionieren lokal; gespeicherte Feeds bleiben offline lesbar. Nur wissenschaftliche APIs und externe KI benötigen Internet.

Für die private KI-Testversion hinterlegst du deinen eigenen Schlüssel, erlaubst die direkte HTTPS-Anbindung und wählst einen verfügbaren Modellnamen. Der Schlüssel wird mit Android Keystore geschützt. Externe Aufrufe können Kosten verursachen; mobile Schlüssel haben Sicherheitsgrenzen. Ein öffentlicher Release benötigt einen abgesicherten Modell-Gateway.

**[Einrichten, Altdaten übernehmen und Sicherheitsgrenzen verstehen →](docs/LOCAL_ANDROID.md)**

```sh
./gradlew :app:assembleDebug
```

> Jede neue Erklärung soll an nachvollziehbar gespeichertes Wissen anknüpfen. Ohne passenden Bezug wird keiner erfunden.
