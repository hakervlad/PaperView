package com.example.aiknowledgefeed.backend.services

import android.content.Context
import androidx.room.withTransaction
import com.example.aiknowledgefeed.backend.database.*
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.repositories.*
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.*
import java.security.MessageDigest

class LocalPdfService(context: Context, private val knowledge: LocalKnowledgeRepository, private val model: ModelClient) {
    init { PDFBoxResourceLoader.init(context.applicationContext) }
    suspend fun importDocument(bytes: ByteArray, filename: String): Document = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) throw ConnectionFailure("pdf_empty")
        if (bytes.size > 10 * 1024 * 1024) throw ConnectionFailure("upload_too_large")
        if (bytes.take(5).toByteArray().toString(Charsets.US_ASCII) != "%PDF-") throw ConnectionFailure("pdf_malformed")
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        knowledge.dao.documents().firstOrNull { it.sha256 == sha }?.let { return@withContext knowledge.documents().first { d -> d.id == it.id } }
        val id = localId(); val chunks = mutableListOf<ChunkRow>()
        try {
            withTimeout(15_000) {
                PDDocument.load(bytes).use { document ->
                    if (document.isEncrypted) throw ConnectionFailure("pdf_encrypted")
                    if (document.numberOfPages !in 1..100) throw ConnectionFailure("pdf_pages")
                    var total = 0
                    val stripper = PDFTextStripper()
                    for (page in 1..document.numberOfPages) {
                        currentCoroutineContext().ensureActive()
                        stripper.startPage = page; stripper.endPage = page
                        val text = stripper.getText(document).replace("\u0000", "").trim()
                        total += text.length
                        if (total > 120_000) throw ConnectionFailure("pdf_text_limit")
                        text.chunked(2000).forEachIndexed { position, part -> if (part.isNotBlank()) chunks += ChunkRow(localId(), id, page, position, part) }
                        if (chunks.size > 60) throw ConnectionFailure("pdf_text_limit")
                    }
                }
            }
        } catch (e: TimeoutCancellationException) { throw ConnectionFailure("pdf_timeout") }
        catch (e: CancellationException) { throw e }
        catch (e: ConnectionFailure) { throw e }
        catch (_: InvalidPasswordException) { throw ConnectionFailure("pdf_encrypted") }
        catch (_: Exception) { throw ConnectionFailure("pdf_malformed") }
        if (chunks.isEmpty()) throw ConnectionFailure("pdf_no_text")
        knowledge.db.withTransaction {
            // Concurrent duplicate imports are resolved inside the same SQLite transaction.
            val duplicate = knowledge.dao.documents().firstOrNull { it.sha256 == sha }
            if (duplicate != null) return@withTransaction knowledge.documents().first { it.id == duplicate.id }
            val clean = filename.substringAfterLast('/').substringAfterLast('\\').filter { !it.isISOControl() }.take(200).ifBlank { "Dokument.pdf" }
            knowledge.dao.document(DocumentRow(id, clean, "extracted", "Lokal extrahiert. Noch keine externe KI-Verarbeitung.", sha))
            knowledge.dao.chunks(chunks); knowledge.changed()
            knowledge.documents().first { it.id == id }
        }
    }
    suspend fun analyze(id: String, consent: Boolean, settings: Settings): Document {
        if (!consent) throw ConnectionFailure("consent_required")
        val document = knowledge.documents().firstOrNull { it.id == id } ?: throw ConnectionFailure("not_found")
        if (document.status == "analyzed") return document
        val chunks = knowledge.dao.chunks(id).take(12)
        if (chunks.isEmpty()) throw ConnectionFailure("pdf_no_text")
        val data = JsonArray().apply { chunks.forEach { add(JsonObject().apply { addProperty("page", it.page); addProperty("text", it.text) }) } }
        val answer = model.complete(settings, "Schema: {\"concepts\":[{\"name\":str,\"description\":str,\"pages\":[int],\"connections\":[concept_name]}]}. Maximal 30 belegte Begriffe mit Seitenbeleg. Erwähnung bedeutet kein bestätigtes Wissen.", data)
        data class Candidate(val name: String, val description: String, val pages: List<Int>, val connections: List<String>)
        val candidates = try {
            answer.getAsJsonArray("concepts").also { require(it.size() <= 30) }.map { element ->
                val c = element.asJsonObject
                Candidate(c.get("name").asString, c.get("description").asString, c.getAsJsonArray("pages").map { it.asInt }, c.getAsJsonArray("connections").map { it.asString })
            }.also { all ->
                require(all.map { normalized(it.name) }.distinct().size == all.size)
                all.forEach { c ->
                    require(c.name.isNotBlank() && c.name.length <= 200 && c.description.length <= 6000 && c.pages.isNotEmpty())
                    require(c.pages.all { p -> chunks.any { it.page == p } } && c.connections.all { n -> all.any { normalized(it.name) == normalized(n) } })
                }
            }
        } catch (_: Exception) { throw ConnectionFailure("llm_invalid_json") }
        return knowledge.db.withTransaction {
            val mapping = candidates.associate { normalized(it.name) to knowledge.addConcept(it.name, it.description).id }
            candidates.forEach { c ->
                val concept = mapping.getValue(normalized(c.name))
                knowledge.dao.evidence(EvidenceRow(id, concept, knowledge.gson.toJson(c.pages)))
                c.connections.map { mapping.getValue(normalized(it)) }.filter { it != concept }.forEach { knowledge.dao.link(LinkRow(concept, it)) }
            }
            val row = knowledge.dao.documents().firstOrNull { it.id == id } ?: throw ConnectionFailure("not_found")
            knowledge.dao.document(row.copy(status = "analyzed", message = "Unbestätigte Konzepte. Maximal zwölf lokal gespeicherte Textabschnitte wurden extern analysiert."))
            knowledge.changed(); knowledge.documents().first { it.id == id }
        }
    }
}
