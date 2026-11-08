package com.example.aiknowledgefeed

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.aiknowledgefeed.backend.database.LocalDatabase
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.repositories.LocalKnowledgeRepository
import com.example.aiknowledgefeed.backend.services.*
import com.google.gson.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LegacyArchiveTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Test fun importKeepsLocalContentAndPreservesDocumentEvidenceAndFeedHistory() = runBlocking {
        val db = LocalDatabase.inMemory(context); val repo = LocalKnowledgeRepository(db)
        try {
            repo.saveProfile(Profile(bachelor_subject = "Informatik"))
            val local = repo.addConcept("Bayes", "Lokale Beschreibung", "confirmed")
            val old = Knowledge("old-bayes", "Bayes", "Alte Beschreibung", "confirmed", document_ids = listOf("doc"))
            val paper = Paper("paper", "Bayes Biomarker", listOf("Fixture"), "Bayes biomarker uncertainty", "2026-11-07", "Wissenschaft", "fixture", "https://example.org/p", "preprint", doi = "10.1000/fixture")
            val item = FeedItem("item", paper.id, paper.title, "Abstractbasierte Demo", listOf(old), "Neue Anwendung", "Bayes verbindet Biomarker", "Demo", "Bayes erklärt Biomarkerunsicherheit", true, paper.url, paper.published_at, paper.field)
            val archive = JsonObject().apply {
                addProperty("format", "paperview-local-v1")
                add("objects", JsonObject().apply {
                    add("profile", repo.gson.toJsonTree(listOf(Profile(bachelor_subject = "Biologie"))))
                    add("knowledge", repo.gson.toJsonTree(listOf(old)))
                    add("documents", repo.gson.toJsonTree(listOf(Document("doc", "thesis.pdf", "analyzed"))))
                    add("papers", repo.gson.toJsonTree(listOf(paper)))
                    add("chunks", JsonParser.parseString("""[{"id":"chunk","document_id":"doc","page":1,"text":"Bayes probability"}]"""))
                    add("evidence", JsonParser.parseString("""[{"id":"e","document_id":"doc","concept_id":"old-bayes","pages":[1]}]"""))
                })
                add("days", JsonArray().apply { add(JsonObject().apply { addProperty("day", "2026-11-08"); addProperty("state", "success"); addProperty("feed", repo.gson.toJson(Feed("2026-11-08", "success", listOf(item)))) }) })
                add("seen", repo.gson.toJsonTree(listOf("doi:10.1000/fixture")))
            }.toString().toByteArray()
            val importer = LegacyArchiveImporter(repo)
            importer.importBytes(archive); importer.importBytes(archive)
            assertEquals("Informatik", repo.profile().bachelor_subject)
            assertEquals(local.id, repo.knowledge().single().id); assertEquals("Lokale Beschreibung", repo.knowledge().single().description)
            assertEquals(listOf("doc"), repo.knowledge().single().document_ids)
            assertEquals("Bayes probability", repo.dao.chunks("doc").single().text)
            assertEquals(local.id, repo.latestFeed().items.single().prior_knowledge.single().id)
            val learned = repo.understood(paper.id)
            assertTrue(learned.connections.contains(local.id)); assertEquals(learned.id, repo.understood(paper.id).id)
        } finally { db.close() }
    }
    @Test fun invalidArchiveDoesNotPartiallyImportOrReplaceData() = runBlocking {
        val db = LocalDatabase.inMemory(context); val repo = LocalKnowledgeRepository(db)
        try {
            repo.saveProfile(Profile(bachelor_subject = "Vorher"))
            for (value in listOf("{}", "not-json", """{"format":"paperview-local-v1","objects":{"knowledge":[{"id":"x","name":"Bad","description":"","status":"unknown"}]}}""")) {
                try { LegacyArchiveImporter(repo).importBytes(value.toByteArray()); fail("Invalid archive accepted") }
                catch (e: ConnectionFailure) { assertEquals("archive_invalid", e.code) }
            }
            assertEquals("Vorher", repo.profile().bachelor_subject); assertTrue(repo.knowledge().isEmpty())
        } finally { db.close() }
    }
}
