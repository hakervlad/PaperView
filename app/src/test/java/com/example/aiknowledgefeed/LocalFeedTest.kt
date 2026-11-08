package com.example.aiknowledgefeed

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.aiknowledgefeed.backend.api.*
import com.example.aiknowledgefeed.backend.database.LocalDatabase
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.repositories.LocalKnowledgeRepository
import com.example.aiknowledgefeed.backend.services.*
import com.google.gson.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocalFeedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Test fun localDailyCacheIsPersistentAndNextDayUsesNewlyUnderstoodKnowledge() = runBlocking {
        val db = LocalDatabase.inMemory(context); val repo = LocalKnowledgeRepository(db)
        var now = Instant.parse("2026-11-08T10:00:00Z"); var searches = 0; var calls = 0
        val contexts = mutableListOf<List<String>>()
        val source = object : PaperSource {
            override suspend fun search(profile: Profile, known: List<Knowledge>, day: LocalDate): PaperSearchResult {
                searches++
                return PaperSearchResult(listOf(Paper("p-$day", "Bayes Biomarker $day", listOf("Fixture"), "Bayes uncertainty biomarkers", day.toString(), "Wissenschaft", "test-fixture", "https://example.org/p-$day", "preprint")))
            }
        }
        val model = object : ModelClient {
            override suspend fun complete(settings: Settings, instruction: String, data: JsonElement): JsonObject {
                calls++; val known = data.asJsonObject.getAsJsonArray("confirmed_knowledge")
                val ids = known.map { it.asJsonObject.get("id").asString }; contexts += ids
                return JsonObject().apply {
                    addProperty("title", "Bayes für Biomarker"); addProperty("summary", "Du kennst bestätigte Bayes-Grundlagen. Das Testabstract überträgt diese auf Biomarker und beschreibt Unsicherheit.")
                    add("concept_ids", JsonArray().apply { ids.forEach { add(it) } })
                    addProperty("new_insight", "Bayes bewertet Biomarker."); addProperty("connection", if (ids.isEmpty()) "" else "Bekanntes Bayes-Wissen erklärt Unsicherheit bei Biomarkern.")
                    addProperty("relevance", "Bekannte Statistik anwenden."); addProperty("takeaway", "Bayes macht Unsicherheit bei Biomarkern nachvollziehbar.")
                }
            }
        }
        try {
            repo.saveProfile(Profile(bachelor_subject = "Biologie", familiar_topics = listOf("Bayes")))
            val service = LocalFeedService(repo, source, LocalPersonalizer(repo, model)) { now }
            val first = service.generate(Settings(), "Europe/Berlin")
            assertEquals(1, searches); assertEquals(1, calls)
            assertEquals(first, service.generate(Settings(), "Europe/Berlin")); assertEquals(1, calls)
            val saved = repo.understood(first.items.single().paper_id)
            assertEquals(saved.id, repo.understood(first.items.single().paper_id).id)
            now = now.plusSeconds(86400)
            val second = LocalFeedService(repo, source, LocalPersonalizer(repo, model)) { now }.generate(Settings(), "Europe/Berlin")
            assertTrue(second.items.single().prior_knowledge.any { it.id == saved.id })
            assertTrue(contexts.last().contains(saved.id)); assertEquals(2, repo.knowledge().size)
            assertEquals(2, repo.dao.successfulFeeds().size)
            assertEquals(second, repo.latestFeed())
        } finally { db.close() }
    }
    @Test fun referencedKnowledgeRequiresAnActualConnectionExplanation() = runBlocking {
        val db = LocalDatabase.inMemory(context); val repo = LocalKnowledgeRepository(db)
        try {
            val known = repo.addConcept("Bayes", "Wahrscheinlichkeit", "confirmed")
            val model = object : ModelClient {
                override suspend fun complete(settings: Settings, instruction: String, data: JsonElement): JsonObject {
                    assertEquals(known.id, data.asJsonObject.getAsJsonArray("confirmed_knowledge").single().asJsonObject.get("id").asString)
                    return JsonParser.parseString("""{"title":"Bayes","summary":"Eine allgemeine Zusammenfassung ohne Erklärung des Bezugs.","concept_ids":["${known.id}"],"new_insight":"Neue Anwendung","connection":"","relevance":"Demo","takeaway":"Neue Erkenntnis"}""").asJsonObject
                }
            }
            val paper = Paper("p", "Bayes Biomarker", emptyList(), "Bayes uncertainty", "2026-11-08", "Wissenschaft", "fixture", "https://example.org/p", "preprint")
            try { LocalPersonalizer(repo, model).explain(paper, Settings()); fail("Missing knowledge explanation accepted") }
            catch (e: ConnectionFailure) { assertEquals("llm_invalid_json", e.code) }
        } finally { db.close() }
    }
    @Test fun inventedKnowledgeReferencesAreRejected() = runBlocking {
        val db = LocalDatabase.inMemory(context); val repo = LocalKnowledgeRepository(db)
        val model = object : ModelClient {
            override suspend fun complete(settings: Settings, instruction: String, data: JsonElement) = JsonParser.parseString("""{"title":"Fake","summary":"Fake","concept_ids":["not-confirmed"],"new_insight":"Fake","connection":"Invented","relevance":"Fake","takeaway":"Fake"}""").asJsonObject
        }
        try {
            val paper = Paper("p", "Unknown", emptyList(), "Abstract", "2026-11-08", "Wissenschaft", "fixture", "https://example.org/p", "unknown")
            try { LocalPersonalizer(repo, model).explain(paper, Settings()); fail("Invented link accepted") }
            catch (e: ConnectionFailure) { assertEquals("llm_invalid_json", e.code) }
        } finally { db.close() }
    }
}
