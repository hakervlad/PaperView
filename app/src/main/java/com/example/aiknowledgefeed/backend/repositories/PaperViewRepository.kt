package com.example.aiknowledgefeed.backend.repositories

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.aiknowledgefeed.backend.api.*
import com.example.aiknowledgefeed.backend.database.LocalDatabase
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.services.*
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

/** On-device application services. No FastAPI client or backend address is used. */
class PaperViewRepository(
    private val context: Context,
    val store: SecureStore = SecureStore(context),
    db: LocalDatabase = LocalDatabase.open(context),
    private val model: ModelClient = DirectModelClient(),
    source: PaperSource = ScientificSources(),
    now: () -> java.time.Instant = java.time.Instant::now,
) {
    val knowledge = LocalKnowledgeRepository(db)
    private val pdf = LocalPdfService(context, knowledge, model)
    private val feeds = LocalFeedService(knowledge, source, LocalPersonalizer(knowledge, model), now)
    suspend fun initialize(): String? {
        if (knowledge.dao.metadata("legacy-cache-v1") != null) return null
        val snapshot = try { store.snapshot() } catch (_: Exception) {
            return "Der alte verschlüsselte Cache konnte nicht übernommen werden. Er bleibt erhalten; alte Serverdaten können als Archiv importiert werden."
        }
        knowledge.importSnapshot(snapshot, "legacy-cache-v1")
        return null
    }
    fun observe() = knowledge.observe()
    suspend fun snapshot() = knowledge.snapshot()
    suspend fun generate(settings: Settings, timezone: String) = feeds.generate(settings, timezone)
    suspend fun analyze(id: String, settings: Settings) = pdf.analyze(id, true, settings)
    private suspend fun read(uri: Uri, limit: Int): ByteArray = withContext(Dispatchers.IO) {
        try { context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer); if (count < 0) break
                if (output.size() + count > limit) throw ConnectionFailure(if (limit == 10 * 1024 * 1024) "upload_too_large" else "archive_invalid")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw ConnectionFailure("local_storage")
        } catch (e: CancellationException) { throw e }
        catch (e: ConnectionFailure) { throw e }
        catch (_: java.io.IOException) { throw ConnectionFailure("local_storage") }
        catch (_: SecurityException) { throw ConnectionFailure("local_storage") }
    }
    suspend fun upload(uri: Uri): Document {
        val filename = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "Dokument.pdf"
        return pdf.importDocument(read(uri, 10 * 1024 * 1024), filename)
    }
    suspend fun importArchive(uri: Uri) = LegacyArchiveImporter(knowledge).importBytes(read(uri, 20 * 1024 * 1024))
    suspend fun test(settings: Settings, progress: (List<ConnectionStep>) -> Unit): List<ConnectionStep> {
        val steps = mutableListOf(ConnectionStep("Lokale Datenbank bereit"), ConnectionStep("Modellanbieter erreichbar"), ConnectionStep("Echter Modellaufruf"))
        fun report(index: Int, status: String, detail: String = "") { steps[index] = steps[index].copy(status = status, detail = detail); progress(steps.toList()) }
        var stage = 0
        try {
            report(0, "Wird geprüft"); snapshot(); report(0, "Erfolgreich", "Wissensverwaltung funktioniert ohne Server.")
            stage = 1; report(1, "Wird geprüft"); report(2, "Wird geprüft")
            val answer = model.complete(settings, "Gib exakt {\"ok\":true} zurück.", JsonObject())
            if (answer.size() != 1 || answer.get("ok")?.isJsonPrimitive != true || answer.get("ok").asJsonPrimitive.isBoolean.not() || !answer.get("ok").asBoolean) throw ConnectionFailure("llm_invalid_json")
            report(1, "Erfolgreich"); report(2, "Erfolgreich", "Modell hat die angeforderte Antwort geliefert.")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val code = FailureMessages.code(e)
            android.util.Log.w("PaperView", "model_test_failed type=${e.javaClass.simpleName} reason=${FailureMessages.message(code)}")
            val reached = code in setOf("llm_invalid_key", "llm_forbidden", "llm_insufficient_quota", "llm_rate_limit", "llm_model_unavailable", "llm_configuration", "llm_invalid_json", "llm_server_error")
            if (stage == 1 && reached) { report(1, "Erfolgreich", "Anbieter hat geantwortet."); report(2, "Fehlgeschlagen", FailureMessages.message(code)) }
            else { report(stage, "Fehlgeschlagen", FailureMessages.message(code)); if (stage == 1) report(2, "Nicht bestätigt") }
        }
        return steps.toList()
    }
}
