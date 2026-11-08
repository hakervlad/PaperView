package com.example.aiknowledgefeed.backend.repositories

import androidx.room.withTransaction
import com.example.aiknowledgefeed.backend.database.*
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.services.ConnectionFailure
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

fun localId(): String = UUID.randomUUID().toString()
fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
fun tokens(value: String): Set<String> = Regex("[\\p{L}\\p{N}_]{3,}").findAll(normalized(value)).map { it.value }.toSet()

class LocalKnowledgeRepository(val db: LocalDatabase) {
    val dao = db.dao()
    val gson = Gson()
    suspend fun changed() = dao.metadata(MetadataRow("revision", localId()))
    fun observe() = dao.observeRevision().map { snapshot() }.flowOn(Dispatchers.IO)
    suspend fun profile(): Profile = dao.profile()?.let { gson.fromJson(it.json, Profile::class.java) } ?: Profile()
    suspend fun knowledge(): List<Knowledge> {
        val links = dao.links(); val evidence = dao.evidence()
        return dao.knowledge().map { row -> Knowledge(row.id, row.name, row.description, row.status,
            links.filter { it.conceptId == row.id }.map { it.relatedId }, row.sourcePaperId,
            evidence.filter { it.conceptId == row.id }.map { it.documentId }) }
    }
    suspend fun documents(): List<Document> {
        val knowledge = knowledge()
        return dao.documents().map { Document(it.id, it.filename, it.status, it.message, knowledge.filter { k -> it.id in k.document_ids }) }
    }
    suspend fun latestFeed(): Feed = dao.successfulFeeds().firstOrNull()?.json?.let { gson.fromJson(it, Feed::class.java) } ?: Feed()
    suspend fun snapshot() = db.withTransaction { Snapshot(latestFeed(), knowledge(), profile(), documents()) }
    suspend fun addConcept(name: String, description: String = "", status: String = "candidate", id: String = localId(), paper: String? = null): Knowledge = db.withTransaction {
        require(name.isNotBlank() && name.length <= 200 && description.length <= 6000)
        require(status in listOf("candidate", "confirmed", "learning"))
        val existing = dao.knowledge().firstOrNull { it.normalizedName == normalized(name) }
        val row = existing?.copy(status = if (status == "confirmed") "confirmed" else existing.status, sourcePaperId = existing.sourcePaperId ?: paper)
            ?: KnowledgeRow(id, name.trim(), normalized(name), description.trim(), status, paper)
        dao.knowledge(row); changed()
        knowledge().first { it.id == row.id }
    }
    suspend fun saveProfile(value: Profile): Profile = db.withTransaction {
        require(value.bachelor_subject.length <= 200 && value.paper_count in 5..10 && value.lookback_days in 7..30)
        require((value.focus_areas + value.interests + value.familiar_topics).size <= 100)
        (value.focus_areas + value.interests + value.familiar_topics).forEach { require(it.length <= 200 && it.isNotBlank()) }
        value.familiar_topics.forEach { addConcept(it, status = "confirmed") }
        dao.profile(ProfileRow(json = gson.toJson(value))); changed(); value
    }
    suspend fun updateKnowledge(id: String, update: KnowledgeUpdate): Knowledge = db.withTransaction {
        require(update.name.isNotBlank() && update.name.length <= 200 && update.description.length <= 6000)
        require(update.status in listOf("confirmed", "candidate", "learning"))
        val rows = dao.knowledge(); val existing = rows.firstOrNull { it.id == id } ?: throw ConnectionFailure("not_found")
        if (rows.any { it.id != id && it.normalizedName == normalized(update.name) }) throw ConnectionFailure("duplicate_concept")
        dao.knowledge(existing.copy(name = update.name.trim(), normalizedName = normalized(update.name), description = update.description.trim(), status = update.status))
        val profile = profile()
        val familiar = profile.familiar_topics.mapNotNull { if (normalized(it) != existing.normalizedName) it else update.name.takeIf { update.status == "confirmed" } }
        dao.profile(ProfileRow(json = gson.toJson(profile.copy(familiar_topics = familiar))))
        changed(); knowledge().first { it.id == id }
    }
    suspend fun deleteKnowledge(id: String) = db.withTransaction {
        val row = dao.knowledge().firstOrNull { it.id == id } ?: throw ConnectionFailure("not_found")
        val profile = profile()
        dao.profile(ProfileRow(json = gson.toJson(profile.copy(familiar_topics = profile.familiar_topics.filterNot { normalized(it) == row.normalizedName }))))
        dao.deleteKnowledge(id); changed()
    }
    suspend fun retrieve(query: String): List<Knowledge> {
        val words = tokens(query); val all = knowledge().filter { it.status == "confirmed" }
        val ranked = all.map { it to tokens(it.name + " " + it.description).intersect(words).size }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(6).map { it.first }
        val neighbors = ranked.flatMap { it.connections }.toSet()
        return (ranked + all.filter { it.id in neighbors && it.id !in ranked.map { k -> k.id } }.take(2)).take(8)
    }
    suspend fun paper(id: String): Paper = dao.paper(id)?.let { gson.fromJson(it.json, Paper::class.java) } ?: throw ConnectionFailure("not_found")
    suspend fun understood(id: String): Knowledge = db.withTransaction {
        val paper = paper(id)
        val explanation = dao.successfulFeeds().asSequence().mapNotNull { it.json }.map { gson.fromJson(it, Feed::class.java) }.flatMap { it.items.asSequence() }.firstOrNull { it.paper_id == id } ?: throw ConnectionFailure("explanation_missing")
        val existing = knowledge().firstOrNull { it.source_paper_id == id }
        val concept = existing ?: addConcept(paper.title.take(200), explanation.takeaway, "confirmed", paper = id)
        val confirmed = updateKnowledge(concept.id, KnowledgeUpdate(concept.name, concept.description, "confirmed"))
        explanation.prior_knowledge.filter { k -> dao.knowledge().any { it.id == k.id } }.forEach { if (it.id != confirmed.id) dao.link(LinkRow(confirmed.id, it.id)) }
        changed(); knowledge().first { it.id == confirmed.id }
    }
    suspend fun removeDocument(id: String) = db.withTransaction {
        if (dao.documents().none { it.id == id }) throw ConnectionFailure("not_found")
        val affected = knowledge().filter { id in it.document_ids }
        dao.deleteDocument(id)
        affected.filter { it.document_ids.size == 1 && it.status != "confirmed" && it.source_paper_id == null }.forEach { dao.deleteKnowledge(it.id) }
        changed()
    }
    suspend fun importSnapshot(snapshot: Snapshot, marker: String) = db.withTransaction {
        if (dao.metadata(marker) != null) return@withTransaction
        if (dao.profile() == null || profile() == Profile()) saveProfile(snapshot.profile)
        snapshot.documents.forEach { if (dao.documents().none { d -> d.id == it.id }) dao.document(DocumentRow(it.id, it.filename, it.status, it.message)) }
        val mapping = mutableMapOf<String, String>()
        snapshot.knowledge.forEach { old ->
            val existing = dao.knowledge().firstOrNull { it.id == old.id || it.normalizedName == normalized(old.name) }
            mapping[old.id] = existing?.id ?: addConcept(old.name, old.description, old.status, old.id, old.source_paper_id).id
        }
        snapshot.knowledge.forEach { old ->
            val id = mapping.getValue(old.id)
            old.connections.mapNotNull { mapping[it] }.filter { it != id }.forEach { dao.link(LinkRow(id, it)) }
            old.document_ids.filter { doc -> dao.documents().any { it.id == doc } }.forEach { dao.evidence(EvidenceRow(it, id, "[]")) }
        }
        if (snapshot.feed.date.isNotBlank() && snapshot.feed.status in listOf("success", "completed", "ready") && dao.feed(snapshot.feed.date) == null) {
            val feed = snapshot.feed.copy(status = "success", items = snapshot.feed.items.map { item -> item.copy(prior_knowledge = item.prior_knowledge.map { k -> k.copy(id = mapping[k.id] ?: k.id) }) })
            feed.items.forEach { item ->
                if (dao.paper(item.paper_id) == null) dao.paper(PaperRow(item.paper_id, null, null, gson.toJson(Paper(item.paper_id, item.title, emptyList(), "", item.published_at, item.category, "legacy-cache", item.source_url, "unknown"))))
                dao.seen(SeenRow("id:"+item.paper_id))
            }
            dao.feed(FeedRow(feed.date, "success", gson.toJson(feed)))
        }
        dao.metadata(MetadataRow(marker, "done")); changed()
    }
}
