package com.example.aiknowledgefeed.frontend.demo

// Frei erfundene redaktionelle Beispiele. Keine Nachrichtenquelle oder Personalisierung.
enum class DemoCategory(val label: String) {
    AI("Künstliche Intelligenz"),
    Technology("Technologie"),
    Science("Wissenschaft"),
    Economy("Wirtschaft"),
    Society("Gesellschaft"),
}

data class DemoArticle(
    val id: String,
    val category: DemoCategory,
    val title: String,
    val summary: String,
    val priorKnowledge: List<String>,
    val topic: String,
    val detail: String,
    val connection: String,
    val takeaway: String,
    val readingMinutes: Int = 3,
    val date: String = "06. November 2026",
    val sourceUrl: String = "",
    val abstractBased: Boolean = false,
    val isDemo: Boolean = true,
)

data class DemoKnowledge(
    val id: String,
    val title: String,
    val category: DemoCategory,
    val description: String,
    val connections: List<String>,
    val articleId: String? = null,
)

object DemoContent {
    const val disclaimer = "Alle Beiträge sind frei erfundene Beispiele. Keine aktuellen Nachrichten."

    val initialKnowledge = listOf(
        DemoKnowledge("agents", "KI-Agenten", DemoCategory.AI,
            "Programme, die Aufgaben in Schritte zerlegen und dafür passende Werkzeuge einsetzen.",
            listOf("APIs", "Neuronale Netze")),
        DemoKnowledge("apis", "APIs", DemoCategory.Technology,
            "Definierte Schnittstellen, über die Anwendungen Informationen austauschen und Funktionen aufrufen.",
            listOf("KI-Agenten", "Datenschutz")),
        DemoKnowledge("networks", "Neuronale Netze", DemoCategory.AI,
            "Lernende Modelle, die Muster in Daten erkennen und auf neue Eingaben übertragen.",
            listOf("KI-Agenten", "Wissenschaft")),
        DemoKnowledge("energy", "Erneuerbare Energien", DemoCategory.Science,
            "Energie aus Quellen wie Sonne und Wind, deren Verfügbarkeit zeitlich schwankt.",
            listOf("Angebot & Nachfrage", "Technologie")),
        DemoKnowledge("markets", "Angebot & Nachfrage", DemoCategory.Economy,
            "Das Zusammenspiel von verfügbarem Angebot und Kaufinteresse beeinflusst Preise und Märkte.",
            listOf("Erneuerbare Energien", "Gesellschaft")),
        DemoKnowledge("privacy", "Datenschutz", DemoCategory.Society,
            "Der bewusste Umgang mit personenbezogenen Daten und die Kontrolle darüber, wer sie nutzen darf.",
            listOf("APIs", "KI-Agenten")),
    )

    val articles = listOf(
        DemoArticle(
            id = "mcp", category = DemoCategory.AI,
            title = "MCP: Eine Sprache für KI-Werkzeuge",
            summary = "Du kennst KI-Agenten und APIs. MCP verbindet beides: Ein gemeinsames Protokoll könnte Agenten den Zugang zu unterschiedlichen Werkzeugen erleichtern.",
            priorKnowledge = listOf("KI-Agenten", "APIs"), topic = "Model Context Protocol",
            detail = "Stell dir ein Forschungsteam vor, das einen Assistenten mit einer Literaturdatenbank, einem Kalender und einer Notiz-App verbindet. Im erfundenen Szenario verwendet es dafür MCP, statt für jede Verbindung einen eigenen Ablauf zu entwickeln.\n\nEin Server beschreibt die verfügbaren Werkzeuge und ihre Eingaben. Ein kompatibler Client kann diese Beschreibung lesen und passende Aufrufe vermitteln. Die eigentliche Funktion bleibt weiterhin in der jeweiligen Anwendung.\n\nDie Idee verspricht weniger individuelle Integrationsarbeit. Sie beantwortet aber nicht automatisch, welche Daten ein Agent sehen darf. Berechtigungen, eine Bestätigung sensibler Aktionen und der Umgang mit unzuverlässigen Inhalten bleiben eigene Aufgaben.",
            connection = "Ein Agent entscheidet, welches Werkzeug hilfreich ist. Eine API stellt dessen Funktion bereit. MCP ergänzt dazwischen ein gemeinsames Beschreibungs- und Kommunikationsformat – ähnlich einem einheitlichen Stecker für unterschiedliche Geräte.",
            takeaway = "MCP kann KI-Anwendungen und Werkzeuge über ein gemeinsames Protokoll verbinden. Ein gemeinsames Format ersetzt keine Zugriffskontrolle.",
        ),
        DemoArticle(
            id = "local-models", category = DemoCategory.Technology,
            title = "Kleine Sprachmodelle, direkt in deiner Hosentasche",
            summary = "Neuronale Netze kennst du bereits. In diesem Szenario wird ein Modell so verkleinert, dass einfache Aufgaben direkt auf dem Smartphone bleiben.",
            priorKnowledge = listOf("Neuronale Netze", "Datenschutz"), topic = "Lokale Sprachmodelle",
            detail = "Eine fiktive Notiz-App sortiert kurze Texte mit einem Sprachmodell direkt auf dem Gerät. Für diese begrenzte Aufgabe braucht sie keine Verbindung zu einem Cloud-Dienst.\n\nDas Team reduziert die Genauigkeit der gespeicherten Modellgewichte und trainiert ein kleineres Modell auf ausgewählte Aufgaben. Dadurch sinkt der Speicherbedarf. Welche Antworten gut gelingen, hängt weiterhin von Modell, Gerät und Aufgabe ab.\n\nLokale Ausführung kann Datenübertragungen vermeiden und Offline-Funktionen ermöglichen. Sie benötigt trotzdem Rechenleistung, Arbeitsspeicher und Energie. Für lange oder komplexe Texte wäre in diesem Beispiel ein größeres Modell weiterhin sinnvoll.",
            connection = "Das Prinzip des neuronalen Netzes bleibt gleich. Verändert werden seine Größe und der Ausführungsort. Dein Wissen über Datenschutz erklärt, warum weniger Datenübertragung interessant ist – aber auch, warum lokale Speicherung geschützt werden muss.",
            takeaway = "Kleinere Sprachmodelle können klar begrenzte Aufgaben lokal bearbeiten. Weniger Speicherbedarf und mehr Datennähe gehen mit Leistungsgrenzen einher.",
        ),
        DemoArticle(
            id = "batteries", category = DemoCategory.Science,
            title = "Ein zweites Leben für überschüssige Sonnenenergie",
            summary = "Du weißt, dass Solarstrom nicht immer dann entsteht, wenn er gebraucht wird. Ein fiktives Speicherprojekt verschiebt Energie vom Mittag in den Abend.",
            priorKnowledge = listOf("Erneuerbare Energien", "Angebot & Nachfrage"), topic = "Stationäre Energiespeicher",
            detail = "In unserem erfundenen Beispiel untersucht eine Gemeinde einen Batteriespeicher neben einer Solaranlage. Mittags lädt er sich auf, abends gibt er einen Teil der gespeicherten Energie wieder ab.\n\nDie Steuerung verbindet Erzeugungsprognosen mit dem erwarteten Verbrauch. Entscheidend sind nicht nur die Kapazität, sondern auch Ladeleistung, Alterung, Wirkungsgrad und die örtliche Netzanbindung.\n\nEin Speicher erzeugt keine neue Energie. Bei jeder Umwandlung entstehen Verluste. Ob sich die Anlage lohnt, müsste anhand konkreter Kosten, Nutzungsprofile und Netzbedingungen geprüft werden; das Szenario nennt deshalb keine angeblichen Versuchsergebnisse.",
            connection = "Erneuerbare Energien erklären die schwankende Erzeugung. Angebot und Nachfrage erklären den Wert einer zeitlichen Verschiebung. Der Speicher verbindet diese beiden Perspektiven, indem er Erzeugung und Nutzung teilweise entkoppelt.",
            takeaway = "Stationäre Speicher verschieben verfügbare Energie in andere Zeiträume. Ihr Nutzen hängt neben der Kapazität von Verlusten, Kosten und dem tatsächlichen Bedarf ab.",
        ),
        DemoArticle(
            id = "dynamic-pricing", category = DemoCategory.Economy,
            title = "Was flexible Strompreise mit deinem Alltag zu tun haben",
            summary = "Angebot und Nachfrage sind dir vertraut. Flexible Tarife übertragen dieses Prinzip auf den Stromverbrauch – etwa auf den Zeitpunkt, zu dem ein Auto lädt.",
            priorKnowledge = listOf("Angebot & Nachfrage", "Erneuerbare Energien"), topic = "Flexible Stromtarife",
            detail = "Ein fiktiver Haushalt vergleicht einen festen Stromtarif mit einem zeitabhängigen Angebot. Eine Ladestation könnte den Ladevorgang innerhalb eines vorgegebenen Zeitfensters verschieben.\n\nDie vereinfachte Idee: Wenn viel Strom verfügbar ist und die Nachfrage niedrig ausfällt, kann eine andere Preisphase entstehen als bei knapper Versorgung. Steuern, Netzentgelte und Vertragsbedingungen beeinflussen jedoch den Endpreis mit.\n\nAutomatisierung kann den Vergleich erleichtern, garantiert aber keine Ersparnis. In der Demo müssten Nutzer zuerst ihre zeitliche Flexibilität, Preisschwankungen und die konkreten Tarifregeln verstehen.",
            connection = "Du kennst den Einfluss von Angebot und Nachfrage auf Preise. Schwankende erneuerbare Erzeugung fügt eine zeitliche Dimension hinzu: Nicht nur wie viel, sondern auch wann verbraucht wird, kann relevant sein.",
            takeaway = "Flexible Tarife machen den Zeitpunkt des Verbrauchs relevant. Ob ein Haushalt profitiert, hängt von Verbrauchsprofil und Vertragsbedingungen ab.",
        ),
        DemoArticle(
            id = "digital-consent", category = DemoCategory.Society,
            title = "Datenteilen mit einem echten Aus-Schalter",
            summary = "Du kennst Datenschutz und APIs. Ein erfundenes Bürgerportal zeigt, wie zeitlich begrenzte Freigaben den Datenaustausch verständlicher machen könnten.",
            priorKnowledge = listOf("Datenschutz", "APIs"), topic = "Zeitlich begrenzte Datenfreigaben",
            detail = "In diesem erfundenen Portal entscheiden Bürger, welche Anwendung bestimmte Nachweise abrufen darf. Jede Freigabe zeigt Zweck, Umfang und Ablaufdatum in verständlicher Sprache.\n\nTechnisch könnte eine API den Zugriff an ein begrenztes Berechtigungstoken binden. Läuft es ab oder wird es widerrufen, müssen weitere Zugriffe abgelehnt werden. Ein sichtbares Protokoll macht vergangene Aufrufe nachvollziehbar.\n\nEin Widerruf holt bereits übermittelte Kopien allerdings nicht automatisch zurück. Gute Gestaltung muss diese Grenze erklären und darf einen einfachen Schalter nicht mit einer vollständigen Löschgarantie verwechseln.",
            connection = "APIs beschreiben den technischen Zugang. Datenschutz stellt die Frage, wer diesen Zugang zu welchem Zweck nutzen darf. Eine begrenzte Freigabe verbindet die Schnittstelle mit einer nachvollziehbaren Entscheidung des Nutzers.",
            takeaway = "Begrenzte Datenfreigaben machen zukünftige Zugriffe kontrollierbarer. Ein Widerruf löscht nicht automatisch bereits übermittelte Daten.",
        ),
        DemoArticle(
            id = "research-agents", category = DemoCategory.AI,
            title = "Ein Recherche-Assistent, der seine Quellen offenlegt",
            summary = "KI-Agenten können Werkzeuge benutzen. Unser Demo-Assistent verknüpft diese Fähigkeit mit Quellenbelegen, damit du seine Aussagen selbst überprüfen kannst.",
            priorKnowledge = listOf("KI-Agenten", "APIs"), topic = "Quellengestützte KI-Recherche",
            detail = "Ein fiktives Hochschulprojekt lässt einen Agenten über eine Suchschnittstelle passende Fachtexte finden. Der Assistent erstellt daraus kurze Notizen mit Verweisen auf die verwendeten Textstellen.\n\nDie Recherche läuft in getrennten Schritten: Suchfrage formulieren, Dokumente auswählen, Textstellen prüfen und daraus eine Antwort erstellen. Jede Stufe kann Fehler einführen. Ein Treffer mit ähnlichen Begriffen ist noch kein belastbarer Beleg.\n\nDie Demo macht deshalb sichtbar, ob eine Aussage nur aus einem Abstract stammt oder anhand eines längeren Texts erklärt wird. Quellenverweise erleichtern die Kontrolle, sind aber kein Beweis dafür, dass die Zusammenfassung korrekt ist.",
            connection = "Du kennst den Agenten als Koordinator und die API als Werkzeugzugang. Quellenbelege ergänzen eine überprüfbare Verbindung zwischen Werkzeugergebnis und Aussage. So wird aus einer flüssigen Antwort eine nachvollziehbare Recherche.",
            takeaway = "Quellengestützte Agenten verbinden Recherchewerkzeuge mit überprüfbaren Belegen. Die Qualität der Auswahl und Interpretation muss trotzdem kontrolliert werden.",
        ),
        DemoArticle(
            id = "materials", category = DemoCategory.Science,
            title = "Neue Materialien finden, bevor das Labor startet",
            summary = "Du kennst neuronale Netze als Mustererkenner. Ein erfundenes Laborteam nutzt dieses Prinzip, um interessante Materialien für Energiespeicher vorzuschlagen.",
            priorKnowledge = listOf("Neuronale Netze", "Erneuerbare Energien"), topic = "KI-gestützte Materialsuche",
            detail = "Ein erfundenes Forschungsteam stellt sich die Frage, welche Materialzusammensetzungen für einen bestimmten Speicher geeignet sein könnten. Ein Modell bewertet Vorschläge anhand bekannter Eigenschaften aus vorhandenen Datensätzen.\n\nStatt jede Kombination sofort herzustellen, priorisiert das Team vielversprechende Kandidaten. Die Vorhersage kann die Planung unterstützen, ersetzt aber keine Messung. Besonders bei unbekannten Materialfamilien kann das Modell falsch liegen.\n\nErst Synthese, Stabilitätstests und weitere Experimente würden zeigen, ob ein Vorschlag praktisch nutzbar ist. Dieses Beispiel beschreibt eine Arbeitsweise und behauptet weder einen Materialdurchbruch noch konkrete Leistungswerte.",
            connection = "Neuronale Netze erkennen Zusammenhänge in Beispieldaten. Dein Wissen über Energiespeicher liefert den Anwendungskontext. Gemeinsam erklären diese Konzepte, warum Modelle bei der Vorauswahl helfen können, ohne Experimente zu ersetzen.",
            takeaway = "KI kann Materialkandidaten priorisieren und Experimente vorbereiten. Vorhersagen bleiben Hypothesen, bis sie experimentell überprüft wurden.",
        ),
        DemoArticle(
            id = "repair", category = DemoCategory.Society,
            title = "Wenn Reparaturwissen zur gemeinsamen Ressource wird",
            summary = "APIs verbinden Anwendungen. In unserem Szenario verknüpft eine offene Schnittstelle Ersatzteildaten mit lokalen Reparaturangeboten und macht vorhandenes Wissen nutzbarer.",
            priorKnowledge = listOf("APIs", "Angebot & Nachfrage"), topic = "Digitale Reparaturnetzwerke",
            detail = "Eine erfundene Stadtplattform bringt Reparaturbetriebe, Ersatzteilanbieter und öffentliche Anleitungen zusammen. Nutzer suchen nach einem Gerätetyp und sehen passende Angebote in ihrer Umgebung.\n\nEine gemeinsame Schnittstelle könnte Teilenummern und Geräteinformationen zwischen den beteiligten Anwendungen austauschen. Die Plattform müsste dennoch prüfen, ob eine Anleitung wirklich zum konkreten Modell passt und ob Ersatzteile verfügbar sind.\n\nDer mögliche Nutzen liegt in leichter auffindbarem Wissen und kürzeren Suchwegen. Faire Zugangsbedingungen, aktuelle Informationen und nachvollziehbare Verantwortlichkeiten wären wichtige Voraussetzungen. Das Szenario ist kein Bericht über eine reale Initiative.",
            connection = "Mit APIs lässt sich verstreutes Wissen technisch verbinden. Angebot und Nachfrage erklären, warum Sichtbarkeit für kleine Reparaturbetriebe wichtig sein kann. Die soziale Wirkung entsteht jedoch erst durch verständliche und verlässliche Angebote.",
            takeaway = "Offene Schnittstellen können Reparaturinformationen auffindbar machen. Verlässliche Inhalte und faire Teilnahmebedingungen bleiben entscheidend.",
        ),
    )
}
