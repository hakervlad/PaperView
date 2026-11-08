package com.example.aiknowledgefeed.backend.services

import androidx.room.withTransaction
import com.example.aiknowledgefeed.backend.api.ScientificSources
import com.example.aiknowledgefeed.backend.database.*
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.repositories.*
import com.google.gson.*
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest
import java.time.LocalDate

/** User-selected archive only. No old server connection, no secret/configuration import. */
class LegacyArchiveImporter(private val knowledge: LocalKnowledgeRepository) {
    data class Report(val concepts: Int, val documents: Int, val feeds: Int, val alreadyImported: Boolean = false)
    suspend fun importBytes(bytes: ByteArray): Report {
        if (bytes.isEmpty() || bytes.size > 20 * 1024 * 1024) throw ConnectionFailure("archive_invalid")
        try {
            val root = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
            require(root.get("format").asString == "paperview-local-v1")
            val objects = root.getAsJsonObject("objects")
            fun array(name: String): List<JsonElement> = objects.getAsJsonArray(name)?.toList().orEmpty().also { require(it.size <= 10000) }
            fun id(value: String) { require(value.matches(Regex("[A-Za-z0-9_.:-]{1,200}"))) }
            fun https(value: String) { require(value.toHttpUrlOrNull()?.let { it.isHttps && it.username.isEmpty() && it.password.isEmpty() } == true) }
            val profile = array("profile").firstOrNull()?.let { knowledge.gson.fromJson(it, Profile::class.java) } ?: Profile()
            val concepts = array("knowledge").map { knowledge.gson.fromJson(it, Knowledge::class.java) }
            require(concepts.map { it.id }.distinct().size == concepts.size && concepts.map { normalized(it.name) }.distinct().size == concepts.size)
            concepts.forEach { k -> id(k.id); require(k.name.isNotBlank() && k.name.length <= 200 && k.description.length <= 6000 && k.status in listOf("confirmed", "candidate", "learning")); k.connections.forEach { id(it) }; k.document_ids.forEach { id(it) }; k.source_paper_id?.let { id(it) } }
            val documents = array("documents").map { knowledge.gson.fromJson(it, Document::class.java) }
            documents.forEach { id(it.id); require(it.filename.isNotBlank() && it.filename.length <= 200 && it.status.length <= 40) }
            require(documents.map { it.id }.distinct().size == documents.size)
            val papers = array("papers").map { knowledge.gson.fromJson(it, Paper::class.java) }
            papers.forEach { id(it.id); https(it.url); require(it.title.isNotBlank() && it.title.length <= 500 && it.abstract.length <= 16000 && it.authors.size <= 100); LocalDate.parse(it.published_at.take(10)) }
            val chunks = array("chunks").mapIndexed { index, element ->
                val c = element.asJsonObject
                ChunkRow(c.get("id").asString, c.get("document_id").asString, c.get("page").asInt, index, c.get("text").asString).also { id(it.id); id(it.documentId); require(it.page in 1..100 && it.text.length <= 2000) }
            }
            chunks.groupBy { it.documentId }.values.forEach { require(it.size <= 60 && it.sumOf { c -> c.text.length } <= 120000) }
            val evidence = array("evidence").map { it.asJsonObject }.map { e ->
                EvidenceRow(e.get("document_id").asString, e.get("concept_id").asString, e.getAsJsonArray("pages").also { pages -> require(pages.size() <= 100 && pages.all { it.asInt in 1..100 }) }.toString())
            }
            val feeds = root.getAsJsonArray("days")?.also { require(it.size() <= 10000) }?.mapNotNull { element ->
                val row = element.asJsonObject
                if (row.get("state")?.asString != "success") null else knowledge.gson.fromJson(row.get("feed").asString, Feed::class.java).also { feed ->
                    LocalDate.parse(feed.date); require(feed.items.size <= 10)
                    feed.items.forEach { item -> id(item.id); id(item.paper_id); https(item.source_url); require(item.title.length <= 500 && item.summary.length <= 12000 && item.prior_knowledge.size <= 8 && item.takeaway.length <= 6000) }
                }
            }.orEmpty()
            val seen = root.getAsJsonArray("seen")?.also { require(it.size() <= 100000) }?.map { it.asString.also { value -> require(value.length <= 1000) } }.orEmpty()
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val marker = "archive:$hash"
            return knowledge.db.withTransaction {
                if (knowledge.dao.metadata(marker) != null) return@withTransaction Report(0, 0, 0, true)
                val beforeConcepts = knowledge.knowledge().size; val beforeDocuments = knowledge.documents().size; val beforeFeeds = knowledge.dao.successfulFeedCount()
                val currentConcepts = knowledge.dao.knowledge()
                concepts.forEach { old -> require(currentConcepts.none { it.id == old.id && it.normalizedName != normalized(old.name) }) }
                documents.forEach { old -> require(knowledge.dao.documents().none { it.id == old.id && it.filename != old.filename }) }
                val paperMapping = mutableMapOf<String, String>()
                papers.forEach { old -> paperMapping[old.id] = knowledge.storePaper(old).id }
                val mappedConcepts = concepts.map { it.copy(source_paper_id = it.source_paper_id?.let { id -> paperMapping[id] ?: id }) }
                knowledge.importSnapshot(Snapshot(knowledge = mappedConcepts, profile = profile, documents = documents), "$marker:snapshot")
                val conceptMap = concepts.associate { old -> old.id to knowledge.knowledge().first { normalized(it.name) == normalized(old.name) }.id }
                chunks.forEach { chunk ->
                    require(knowledge.dao.documents().any { it.id == chunk.documentId })
                    val existing = knowledge.dao.chunks(chunk.documentId).firstOrNull { it.id == chunk.id }
                    if (existing == null) knowledge.dao.chunks(listOf(chunk)) else require(existing.text == chunk.text && existing.page == chunk.page)
                }
                documents.forEach { imported ->
                    val row = knowledge.dao.documents().first { it.id == imported.id }
                    if (row.status == "needs_text" && knowledge.dao.chunks(imported.id).isNotEmpty()) knowledge.dao.document(row.copy(status = imported.status, message = imported.message))
                }
                evidence.forEach { row ->
                    val target = conceptMap[row.conceptId] ?: throw IllegalArgumentException()
                    require(knowledge.dao.documents().any { it.id == row.documentId })
                    knowledge.dao.evidence(row.copy(conceptId = target))
                }
                feeds.forEach { old ->
                    if (knowledge.dao.feed(old.date)?.status != "success") {
                        val feed = old.copy(status = "success", items = old.items.map { item -> item.copy(paper_id = paperMapping[item.paper_id] ?: item.paper_id, prior_knowledge = item.prior_knowledge.map { k -> k.copy(id = conceptMap[k.id] ?: k.id) }) })
                        feed.items.forEach { item ->
                            if (knowledge.dao.paper(item.paper_id) == null) knowledge.dao.paper(PaperRow(item.paper_id, null, null, knowledge.gson.toJson(Paper(item.paper_id, item.title, emptyList(), "", item.published_at, item.category, "legacy-archive", item.source_url, "unknown"))))
                            val source = knowledge.paper(item.paper_id)
                            ScientificSources.identifiers(source).forEach { knowledge.dao.seen(SeenRow(it)) }
                        }
                        knowledge.dao.feed(FeedRow(feed.date, "success", knowledge.gson.toJson(feed)))
                    }
                }
                seen.forEach { knowledge.dao.seen(SeenRow(it)) }
                knowledge.dao.metadata(MetadataRow(marker, "done")); knowledge.changed()
                Report(knowledge.knowledge().size - beforeConcepts, knowledge.documents().size - beforeDocuments, knowledge.dao.successfulFeedCount() - beforeFeeds)
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { throw ConnectionFailure("archive_invalid") }
    }
}
