package com.example.aiknowledgefeed

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.aiknowledgefeed.backend.api.*
import com.example.aiknowledgefeed.backend.database.LocalDatabase
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.repositories.*
import com.example.aiknowledgefeed.backend.services.*
import com.google.gson.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** No FastAPI process, HTTP listener or PC address participates in this workflow. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocalWorkflowTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Test fun pdfToPersonalizationPersistsAcrossReopenAndUsesLearnedKnowledgeTomorrow() = runBlocking {
        val path = File(context.noBackupFilesDir, "complete-local-workflow.db"); path.delete()
        var db = LocalDatabase.create(context, path.absolutePath)
        var now = Instant.parse("2026-11-08T09:00:00Z")
        var calls = 0; var searches = 0; val contexts = mutableListOf<List<String>>()
        val model = object : ModelClient {
            override suspend fun complete(settings: Settings, instruction: String, data: JsonElement): JsonObject {
                calls++
                if (data.isJsonArray) return JsonParser.parseString("""{"concepts":[{"name":"Bayes","description":"Wahrscheinlichkeit und Unsicherheit","pages":[1],"connections":[]}]}""").asJsonObject
                val known = data.asJsonObject.getAsJsonArray("confirmed_knowledge").map { it.asJsonObject.get("id").asString }; contexts += known
                return JsonObject().apply {
                    addProperty("title", "Bayes für Biomarker"); addProperty("summary", "Dein bestätigtes Bayes-Wissen erklärt, wie das Testabstract Unsicherheit bei Biomarkern einordnet.")
                    add("concept_ids", JsonArray().apply { known.forEach { add(it) } }); addProperty("new_insight", "Bayes für Biomarker.")
                    addProperty("connection", if (known.isEmpty()) "" else "Dein bekanntes Bayes-Konzept beschreibt die Unsicherheit der neuen Biomarker-Methode.")
                    addProperty("relevance", "Statistisches Wissen anwenden."); addProperty("takeaway", "Bayes beschreibt Unsicherheit bei Biomarkern.")
                }
            }
        }
        val source = object : PaperSource {
            override suspend fun search(profile: Profile, known: List<Knowledge>, day: LocalDate): PaperSearchResult {
                searches++; return PaperSearchResult(listOf(Paper("paper-$day", "Bayes Biomarker $day", listOf("Fixture"), "Bayes probability biomarker uncertainty", day.toString(), "Wissenschaft", "fixture", "https://example.org/p-$day", "preprint")))
            }
        }
        val settings = Settings(directApiConsent = true, apiKey = "fixture-not-a-real-key", model = "fixture")
        try {
            var repository = PaperViewRepository(context, db = db, model = model, source = source, now = { now })
            repository.initialize(); repository.knowledge.saveProfile(Profile(bachelor_subject = "Biologie", interests = listOf("Biomarker")))
            val pdf = LocalPdfService(context, repository.knowledge, model)
            val bytes = javaClass.getResourceAsStream("/thesis.pdf")!!.use { it.readBytes() }
            val document = pdf.importDocument(bytes, "thesis.pdf"); assertEquals(0, calls)
            val extracted = pdf.analyze(document.id, true, settings)
            val candidate = extracted.concepts.single(); assertEquals("candidate", candidate.status)
            assertTrue(repository.knowledge.retrieve("Bayes").isEmpty())
            repository.knowledge.updateKnowledge(candidate.id, KnowledgeUpdate(candidate.name, candidate.description, "confirmed"))
            val first = repository.generate(settings, "Europe/Berlin")
            assertEquals(candidate.id, first.items.single().prior_knowledge.single().id)
            assertTrue(first.items.single().abstract_based)
            val saved = repository.knowledge.understood(first.items.single().paper_id)
            assertTrue(saved.connections.contains(candidate.id)); assertEquals(2, calls)
            val snapshot = repository.snapshot()
            db.close(); db = LocalDatabase.create(context, path.absolutePath)
            repository = PaperViewRepository(context, db = db, model = model, source = source, now = { now })
            repository.initialize(); assertEquals(snapshot, repository.snapshot())
            assertEquals(first, repository.generate(settings, "Europe/Berlin")); assertEquals(1, searches); assertEquals(2, calls)
            assertEquals(saved.id, repository.knowledge.understood(first.items.single().paper_id).id)
            now = now.plusSeconds(86400)
            val next = repository.generate(settings, "Europe/Berlin")
            assertTrue(next.items.single().prior_knowledge.any { it.id == saved.id }); assertTrue(contexts.last().contains(saved.id))
            assertEquals(2, repository.knowledge.dao.successfulFeedCount())
            repository.knowledge.removeDocument(document.id)
            assertTrue(repository.knowledge.dao.chunks(document.id).isEmpty())
            assertTrue(repository.knowledge.knowledge().any { it.id == candidate.id && it.document_ids.isEmpty() })
        } finally { db.close(); path.delete() }
    }
    @Test fun parallelDailyRequestsClaimOnlyOneIssueAndFailurePreservesPreviousFeed() = runBlocking {
        val db = LocalDatabase.inMemory(context); val knowledge = LocalKnowledgeRepository(db)
        var now = Instant.parse("2026-11-08T09:00:00Z"); var searches = 0; var failSource = false
        val source = object : PaperSource {
            override suspend fun search(profile: Profile, known: List<Knowledge>, day: LocalDate): PaperSearchResult {
                searches++; delay(100)
                if (failSource) throw ConnectionFailure("source_unavailable")
                return PaperSearchResult(emptyList())
            }
        }
        val model = object : ModelClient { override suspend fun complete(settings: Settings, instruction: String, data: JsonElement): JsonObject = error("No paper means no paid call") }
        try {
            val feed = LocalFeedService(knowledge, source, LocalPersonalizer(knowledge, model)) { now }
            val results = listOf(async { runCatching { feed.generate(Settings(), "Europe/Berlin") } }, async { runCatching { feed.generate(Settings(), "Europe/Berlin") } }).awaitAll()
            assertTrue(results.any { it.isSuccess });
            results.filter { it.isFailure }.forEach { assertEquals("feed_busy", (it.exceptionOrNull() as ConnectionFailure).code) }
            assertEquals(1, searches); assertEquals(1, knowledge.dao.successfulFeedCount())
            val old = knowledge.latestFeed(); now = now.plusSeconds(86400); failSource = true
            try { feed.generate(Settings(), "Europe/Berlin"); fail("Source outage") } catch (e: ConnectionFailure) { assertEquals("source_unavailable", e.code) }
            assertEquals(old, knowledge.latestFeed())
            assertEquals("failed", knowledge.dao.feed("2026-11-09")!!.status)
            try { feed.generate(Settings(), "Europe/Berlin"); fail("Cooldown") } catch (e: ConnectionFailure) { assertEquals("feed_cooldown", e.code) }
            now = now.plusSeconds(301); failSource = false
            assertEquals("2026-11-09", feed.generate(Settings(), "Europe/Berlin").date)
            assertEquals(2, knowledge.dao.successfulFeedCount())
        } finally { db.close() }
    }
}
