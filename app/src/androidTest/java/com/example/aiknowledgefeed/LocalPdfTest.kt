package com.example.aiknowledgefeed

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aiknowledgefeed.backend.database.LocalDatabase
import com.example.aiknowledgefeed.backend.repositories.LocalKnowledgeRepository
import com.example.aiknowledgefeed.backend.services.*
import com.example.aiknowledgefeed.backend.models.Settings
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalPdfTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Test fun localImportExtractsPagesWithoutCallingAnyModelAndIsIdempotent() = runBlocking {
        val db = LocalDatabase.inMemory(context); val repo = LocalKnowledgeRepository(db)
        var calls = 0
        val model = object : ModelClient { override suspend fun complete(settings: Settings, instruction: String, data: com.google.gson.JsonElement): JsonObject { calls++; error("No model during local import") } }
        try {
            val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("thesis.pdf").use { it.readBytes() }
            val service = LocalPdfService(context, repo, model)
            val document = service.importDocument(bytes, "../thesis.pdf")
            assertEquals("thesis.pdf", document.filename); assertEquals("extracted", document.status); assertEquals(0, calls)
            val chunks = repo.dao.chunks(document.id); assertTrue(chunks.isNotEmpty()); assertEquals(1, chunks.first().page)
            assertTrue(chunks.first().text.contains("Bayes"))
            assertEquals(document.id, service.importDocument(bytes, "duplicate.pdf").id)
            assertEquals(1, repo.documents().size)
            try { service.analyze(document.id, false, Settings()); fail("Consent required") } catch (e: ConnectionFailure) { assertEquals("consent_required", e.code) }
            assertEquals(0, calls)
        } finally { db.close() }
    }
    @Test fun emptyBrokenOversizedPdfsFailWithoutPartialRecords() = runBlocking {
        val db = LocalDatabase.inMemory(context); val repo = LocalKnowledgeRepository(db)
        try {
            val service = LocalPdfService(context, repo, DirectModelClient())
            for ((bytes, code) in listOf(ByteArray(0) to "pdf_empty", "%PDF- broken".toByteArray() to "pdf_malformed", ByteArray(10 * 1024 * 1024 + 1) to "upload_too_large")) {
                try { service.importDocument(bytes, "fixture.pdf"); fail("Expected $code") } catch (e: ConnectionFailure) { assertEquals(code, e.code) }
            }
            assertTrue(repo.documents().isEmpty())
        } finally { db.close() }
    }
}
