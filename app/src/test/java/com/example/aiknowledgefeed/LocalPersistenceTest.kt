package com.example.aiknowledgefeed

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.aiknowledgefeed.backend.database.LocalDatabase
import com.example.aiknowledgefeed.backend.repositories.LocalKnowledgeRepository
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.services.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocalPersistenceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Test fun realRoomDatabaseSurvivesReopenAndKnowledgeRemainsOffline() = runBlocking {
        val file = File(context.noBackupFilesDir, "roundtrip.db"); file.delete()
        var db = LocalDatabase.create(context, file.absolutePath)
        var repo = LocalKnowledgeRepository(db)
        repo.saveProfile(Profile(bachelor_subject = "Informatik", familiar_topics = listOf("Bayes")))
        val known = repo.knowledge().single(); db.close()
        db = LocalDatabase.create(context, file.absolutePath); repo = LocalKnowledgeRepository(db)
        try {
            assertEquals("Informatik", repo.profile().bachelor_subject)
            assertEquals(known.id, repo.knowledge().single().id)
            assertEquals(known.id, repo.retrieve("Bayes uncertainty").single().id)
            repo.saveProfile(repo.profile()); assertEquals(1, repo.knowledge().size)
            repo.deleteKnowledge(known.id); assertTrue(repo.knowledge().isEmpty()); assertTrue(repo.profile().familiar_topics.isEmpty())
        } finally { db.close(); file.delete() }
    }
    @Test fun candidatesAndLegacyCacheImportRespectConfirmationAndExistingLocalContent() = runBlocking {
        val db = LocalDatabase.inMemory(context); val repo = LocalKnowledgeRepository(db)
        try {
            val candidate = repo.addConcept("Bayes", "Lokaler Text")
            assertTrue(repo.retrieve("Bayes").isEmpty())
            repo.updateKnowledge(candidate.id, KnowledgeUpdate(candidate.name, candidate.description, "confirmed"))
            repo.importSnapshot(Snapshot(knowledge = listOf(Knowledge("legacy", "Bayes", "Alter Text", "confirmed")), profile = Profile(bachelor_subject = "Biologie")), "cache-v1")
            repo.importSnapshot(Snapshot(), "cache-v1")
            assertEquals(1, repo.knowledge().size)
            assertEquals(candidate.id, repo.knowledge().single().id)
            assertEquals("Lokaler Text", repo.knowledge().single().description)
            assertEquals("Biologie", repo.profile().bachelor_subject)
        } finally { db.close() }
    }
    @Test fun cachedDocumentMetadataDoesNotPretendPdfTextIsAlreadyLocal() = runBlocking {
        val db = LocalDatabase.inMemory(context); val repo = LocalKnowledgeRepository(db)
        try {
            repo.importSnapshot(Snapshot(documents = listOf(Document("legacy-doc", "thesis.pdf", "extracted"))), "legacy-document")
            val document = repo.documents().single()
            assertEquals("needs_text", document.status)
            assertTrue(document.message!!.contains("Textabschnitte fehlen"))
            assertTrue(repo.dao.chunks(document.id).isEmpty())
        } finally { db.close() }
    }
    @Test fun pdfImportIsLocalAndDeduplicatedAndRequiresConsentBeforeModelUse() = runBlocking {
        val db = LocalDatabase.inMemory(context); val repo = LocalKnowledgeRepository(db)
        var calls = 0
        val model = object : ModelClient {
            override suspend fun complete(settings: Settings, instruction: String, data: com.google.gson.JsonElement): com.google.gson.JsonObject { calls++; throw ConnectionFailure("llm_unavailable") }
        }
        try {
            val service = LocalPdfService(context, repo, model)
            val bytes = javaClass.getResourceAsStream("/thesis.pdf")!!.use { it.readBytes() }
            val document = service.importDocument(bytes, "../test.pdf")
            assertEquals("test.pdf", document.filename); assertEquals("extracted", document.status)
            assertTrue(repo.dao.chunks(document.id).first().text.contains("Bayes"))
            assertEquals(document.id, service.importDocument(bytes, "duplicate.pdf").id); assertEquals(0, calls)
            try { service.analyze(document.id, false, Settings()); fail("Consent") } catch (e: ConnectionFailure) { assertEquals("consent_required", e.code) }
            assertEquals(0, calls)
            try { service.analyze(document.id, true, Settings()); fail("Model failure") } catch (e: ConnectionFailure) { assertEquals("llm_unavailable", e.code) }
            assertEquals("extracted", repo.documents().single().status); assertTrue(repo.knowledge().isEmpty())
            fun blankPdf(pages: Int): ByteArray {
                val output = java.io.ByteArrayOutputStream()
                com.tom_roush.pdfbox.pdmodel.PDDocument().use { pdf -> repeat(pages) { pdf.addPage(com.tom_roush.pdfbox.pdmodel.PDPage()) }; pdf.save(output) }
                return output.toByteArray()
            }
            for ((input, code) in listOf(blankPdf(1) to "pdf_no_text", blankPdf(101) to "pdf_pages", ByteArray(0) to "pdf_empty", "%PDF- broken".toByteArray() to "pdf_malformed", ByteArray(10 * 1024 * 1024 + 1) to "upload_too_large")) {
                try { service.importDocument(input, "bad.pdf"); fail(code) } catch (e: ConnectionFailure) { assertEquals(code, e.code) }
            }
            assertEquals(1, repo.documents().size)
            repo.removeDocument(document.id); assertTrue(repo.dao.chunks(document.id).isEmpty())
        } finally { db.close() }
    }
}
