package com.example.aiknowledgefeed.backend.services

import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import com.google.gson.JsonParser
import retrofit2.HttpException
import java.io.IOException
import java.io.EOFException
import java.net.*
import javax.net.ssl.SSLException

class ConnectionFailure(val code: String) : IOException()
object FailureMessages {
    fun code(error: Exception): String = when (error) {
        is android.database.sqlite.SQLiteException -> "local_storage"
        is ConnectionFailure -> error.code
        is SSLException -> "tls_error"
        is SocketTimeoutException -> "timeout"
        is UnknownHostException -> "dns_error"
        is ConnectException, is NoRouteToHostException -> "backend_unreachable"
        is HttpException -> runCatching { JsonParser.parseString(error.response()?.errorBody()?.string()).asJsonObject.getAsJsonObject("error").get("code").asString }.getOrNull() ?: "http_${error.code()}"
        is JsonParseException, is MalformedJsonException, is EOFException, is KotlinNullPointerException -> "invalid_response"
        is IOException -> "network_error"
        is IllegalArgumentException, is IllegalStateException -> "configuration_error"
        else -> "unexpected_error"
    }
    fun message(code: String): String = when (code) {
        "source_unavailable" -> "Wissenschaftliche Quelle nicht erreichbar. Internetverbindung und Quellenstatus prüfen."
        "source_rate_limit" -> "Anfragelimit einer wissenschaftlichen Quelle erreicht. Bitte später erneut versuchen."
        "source_invalid" -> "Ungültige Antwort einer wissenschaftlichen Quelle. Der gespeicherte Feed bleibt erhalten."
        "profile_required" -> "Bitte zuerst Studienfach, Interessen oder bestätigtes Vorwissen speichern."
        "direct_consent_required" -> "Direkte KI-Anbindung für die private Testversion zuerst ausdrücklich erlauben."
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
        "backend_unreachable" -> "Backend nicht erreichbar. Server starten und die Backend-Adresse prüfen. localhost ist ohne ADB-Weiterleitung das Smartphone; 10.0.2.2 gilt nur für Emulatoren."
        "dns_error" -> "Backend-Adresse kann nicht aufgelöst werden. Internetverbindung und DNS/Hostnamen prüfen."
        "tls_error" -> "TLS-Zertifikat nicht vertrauenswürdig oder Hostname falsch. Gültiges HTTPS-Zertifikat verwenden; die Zertifikatsprüfung bleibt aktiv."
        "timeout", "llm_timeout" -> "Zeitüberschreitung. Backend beziehungsweise Modellanbieter antwortet nicht rechtzeitig."
        "unauthorized", "http_401", "http_403" -> "Backend-Zugriff verweigert. Backend-Zugriffsschlüssel prüfen (dies ist nicht der OpenAI-API-Key)."
        "llm_invalid_key" -> "API-Key des Modellanbieters ungültig oder abgelaufen. Gespeicherten KI-Schlüssel prüfen."
        "llm_forbidden" -> "Der Modellanbieter verweigert den Zugriff. Projekt-, Regions- und Modellberechtigungen prüfen."
        "llm_insufficient_quota" -> "API-Guthaben oder Ausgabenlimit des Modellanbieters ausgeschöpft. Abrechnung beim Anbieter prüfen."
        "llm_rate_limit", "http_429" -> "Anfragelimit erreicht. Bitte später erneut versuchen."
        "llm_model_unavailable" -> "Modell nicht verfügbar. Exakten Modellnamen und API-Basis-URL prüfen."
        "llm_configuration" -> "Modellanfrage abgelehnt. Modellname, Basis-URL und unterstützte Modellparameter prüfen."
        "llm_missing_key" -> "Kein Modellschlüssel konfiguriert. Eigenen Schlüssel speichern oder serverseitigen Schlüssel einrichten."
        "llm_missing_model" -> "Modellname fehlt. Exakten Modellnamen in der App oder im Backend konfigurieren."
        "provider_forbidden" -> "KI-Anbieter nicht freigegeben oder DNS-Adresse nicht zulässig. HTTPS-Basis-URL und serverseitige Host-Allowlist prüfen."
        "llm_server_error" -> "Serverfehler beim Modellanbieter. Das Backend und der Anbieter wurden erreicht. Bitte später erneut versuchen."
        "llm_unavailable" -> "Modellanbieter nicht erreichbar oder Serverfehler beim Anbieter. Backend wurde erreicht."
        "llm_tls" -> "TLS-Verbindung zum Modellanbieter fehlgeschlagen. Zertifikate und Server-Uhrzeit prüfen."
        "https_required" -> "Eigene API-Keys benötigen eine HTTPS-Verbindung zum Backend. Reines HTTP oder eine Emulator-Adresse reicht auf dem Smartphone nicht."
        "invalid_response", "llm_invalid_json" -> "Ungültige Backend- oder Modellantwort. API-Vertrag und JSON-Antwort prüfen."
        "configuration_error" -> "Ungültige Konfiguration. HTTPS-Backend-URL, Backend-Zugriffsschlüssel und Eingaben prüfen."
        "network_error" -> "Netzwerkverbindung unterbrochen. Internet, Backend und Firewall prüfen. Der letzte gespeicherte Stand bleibt sichtbar."
        "http_404", "not_found" -> "Backend-Endpunkt oder Eintrag nicht gefunden. Backend-URL und Serverversion prüfen."
        "explanation_missing" -> "Vor dem Speichern muss eine personalisierte Erklärung zu diesem Paper erstellt werden."
        "duplicate_concept" -> "Dieses Wissenskonzept ist bereits vorhanden."
        "validation_error", "http_422" -> "Eingaben vom Backend abgelehnt. Pflichtfelder und zulässige Werte prüfen."
        "pdf_no_text" -> "Die PDF enthält keinen extrahierbaren Text. Gescannte Dokumente benötigen OCR."
        "consent_required" -> "Für die externe Dokumentanalyse ist deine Zustimmung erforderlich."
        "upload_too_large" -> "Die PDF überschreitet das Upload-Limit von 10 MB."
        else -> if (code.startsWith("http_5") || code == "internal_error") "Serverfehler im Backend. Serverstatus und sichere Diagnoseprotokolle prüfen." else "Verarbeitung fehlgeschlagen. Bitte erneut versuchen."
    }
}
