package com.example.aiknowledgefeed.backend.services

import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import java.io.IOException
import java.io.EOFException
import java.net.*
import javax.net.ssl.SSLException

class ConnectionFailure(val code: String) : IOException()
object FailureMessages {
    fun code(error: Exception): String = when (error) {
        is SecurityException -> "local_storage"
        is android.database.sqlite.SQLiteException -> "local_storage"
        is ConnectionFailure -> error.code
        is SSLException -> "tls_error"
        is SocketTimeoutException -> "timeout"
        is UnknownHostException -> "dns_error"
        is ConnectException, is NoRouteToHostException -> "service_unreachable"
        is JsonParseException, is MalformedJsonException, is EOFException, is KotlinNullPointerException -> "invalid_response"
        is IOException -> "network_error"
        is IllegalArgumentException, is IllegalStateException -> "configuration_error"
        else -> "unexpected_error"
    }
    fun message(code: String): String = when (code) {
        "key_rotation_required" -> "Der zuvor offengelegte Schlüssel darf nicht verwendet werden. Bitte widerrufen und einen neu erzeugten API-Key sicher eingeben."
        "source_unavailable" -> "Wissenschaftliche Quelle nicht erreichbar. Internetverbindung und Quellenstatus prüfen."
        "source_rate_limit" -> "Anfragelimit einer wissenschaftlichen Quelle erreicht. Bitte später erneut versuchen."
        "source_invalid" -> "Ungültige Antwort einer wissenschaftlichen Quelle. Der gespeicherte Feed bleibt erhalten."
        "profile_required" -> "Bitte zuerst Studienfach, Interessen oder bestätigtes Vorwissen speichern."
        "direct_consent_required" -> "Direkte KI-Anbindung für die private Testversion zuerst ausdrücklich erlauben."
        "document_text_missing" -> "Die Textabschnitte des alten Dokuments fehlen auf diesem Gerät. Serverarchiv importieren oder PDF erneut lokal auswählen."
        "pdf_empty" -> "Die PDF-Datei ist leer."
        "pdf_malformed" -> "Die PDF ist beschädigt oder ungültig."
        "pdf_encrypted" -> "Die PDF ist verschlüsselt. Bitte eine entschlüsselte Kopie importieren."
        "pdf_pages" -> "Die PDF muss zwischen einer und 100 Seiten enthalten."
        "pdf_text_limit" -> "Die PDF überschreitet das lokale Textlimit von 120.000 Zeichen beziehungsweise 60 Abschnitten."
        "pdf_timeout" -> "Zeitlimit bei der lokalen PDF-Verarbeitung erreicht."
        "feed_timeout" -> "Zeitlimit bei der Feed-Erstellung erreicht. Der letzte Feed bleibt verfügbar."
        "feed_busy" -> "Die heutige Ausgabe wird bereits erstellt."
        "feed_cooldown" -> "Feed-Erstellung fehlgeschlagen. Bitte nach der Wartezeit erneut versuchen; der letzte Feed bleibt erhalten."
        "local_storage" -> "Lokales Speichern fehlgeschlagen. Freien Gerätespeicher und App-Zugriff prüfen."
        "archive_invalid" -> "Ungültiges oder zu großes PaperView-Archiv. Es wurden keine lokalen Daten überschrieben."
        "service_unreachable" -> "Externer Dienst nicht erreichbar. Internetverbindung und Anbieter-Adresse prüfen."
        "dns_error" -> "Anbieter-Adresse kann nicht aufgelöst werden. Internetverbindung und DNS/Hostnamen prüfen."
        "tls_error" -> "TLS-Zertifikat nicht vertrauenswürdig oder Hostname falsch. Gültiges HTTPS-Zertifikat verwenden; die Zertifikatsprüfung bleibt aktiv."
        "timeout", "llm_timeout" -> "Zeitüberschreitung. Der externe Dienst antwortet nicht rechtzeitig."
        "unauthorized", "http_401", "http_403" -> "Zugriff auf den externen Dienst verweigert. Konfigurierte Zugangsdaten prüfen."
        "llm_invalid_key" -> "API-Key des Modellanbieters ungültig oder abgelaufen. Gespeicherten KI-Schlüssel prüfen."
        "llm_forbidden" -> "Der Modellanbieter verweigert den Zugriff. Projekt-, Regions- und Modellberechtigungen prüfen."
        "llm_insufficient_quota" -> "API-Guthaben oder Ausgabenlimit des Modellanbieters ausgeschöpft. Abrechnung beim Anbieter prüfen."
        "llm_rate_limit", "http_429" -> "Anfragelimit erreicht. Bitte später erneut versuchen."
        "llm_model_unavailable" -> "Modell nicht verfügbar. Exakten Modellnamen und API-Basis-URL prüfen."
        "llm_configuration" -> "Modellanfrage abgelehnt. Modellname, Basis-URL und unterstützte Modellparameter prüfen."
        "llm_missing_key" -> "Kein Modellschlüssel konfiguriert. Deinen persönlichen API-Key in den KI-Einstellungen speichern."
        "llm_missing_model" -> "Modellname fehlt. Einen verfügbaren, exakten Modellnamen in den KI-Einstellungen eintragen."
        "provider_forbidden" -> "KI-Anbieteradresse ist nicht zulässig. Die bewusst gewählte HTTPS-Basis-URL prüfen."
        "llm_server_error" -> "Serverfehler beim Modellanbieter. Der Anbieter wurde erreicht. Bitte später erneut versuchen."
        "llm_unavailable" -> "Modellanbieter nicht erreichbar. Internetverbindung und Anbieterstatus prüfen."
        "llm_tls" -> "TLS-Verbindung zum Modellanbieter fehlgeschlagen. Zertifikate und Server-Uhrzeit prüfen."
        "https_required" -> "API-Keys und private Inhalte dürfen nur über HTTPS an den bewusst konfigurierten Modellanbieter übertragen werden."
        "invalid_response", "llm_invalid_json" -> "Ungültige Antwort des externen Dienstes. Modellkonfiguration und Antwortformat prüfen."
        "configuration_error" -> "Ungültige Eingabe oder KI-Konfiguration. HTTPS-Anbieter-URL, exakten Modellnamen und Pflichtfelder prüfen."
        "network_error" -> "Netzwerkverbindung unterbrochen. Internetverbindung und Anbieterstatus prüfen. Der letzte gespeicherte Stand bleibt sichtbar."
        "http_404", "not_found" -> "Dieser Eintrag wurde in der lokalen Datenbank nicht gefunden."
        "explanation_missing" -> "Vor dem Speichern muss eine personalisierte Erklärung zu diesem Paper erstellt werden."
        "duplicate_concept" -> "Dieses Wissenskonzept ist bereits vorhanden."
        "validation_error", "http_422" -> "Eingaben sind ungültig. Pflichtfelder und zulässige Werte prüfen."
        "pdf_no_text" -> "Die PDF enthält keinen extrahierbaren Text. Gescannte Dokumente benötigen OCR."
        "consent_required" -> "Für die externe Dokumentanalyse ist deine Zustimmung erforderlich."
        "upload_too_large" -> "Die PDF überschreitet das Upload-Limit von 10 MB."
        else -> if (code.startsWith("http_5") || code == "internal_error") "Interner Dienstfehler. Sichere Diagnoseprotokolle prüfen." else "Verarbeitung fehlgeschlagen. Bitte erneut versuchen."
    }
}
