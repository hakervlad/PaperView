package com.example.aiknowledgefeed.backend.services

import androidx.room.withTransaction
import com.example.aiknowledgefeed.backend.api.PaperSource
import com.example.aiknowledgefeed.backend.api.ScientificSources
import com.example.aiknowledgefeed.backend.database.*
import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.repositories.LocalKnowledgeRepository
import com.example.aiknowledgefeed.backend.repositories.tokens
import com.example.aiknowledgefeed.backend.repositories.localId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.time.ZoneId

class LocalFeedService(
    private val knowledge: LocalKnowledgeRepository,
    private val source: PaperSource,
    private val personalizer: LocalPersonalizer,
    private val now: () -> Instant = Instant::now,
) {
    suspend fun generate(settings: Settings, timezone: String): Feed {
        val today = now().atZone(ZoneId.of(timezone)).toLocalDate()
        val day = today.toString()
        val owner = localId()
        val cached = knowledge.db.withTransaction {
            val row = knowledge.dao.feed(day); val time = now().toEpochMilli()
            if (row?.status == "success") return@withTransaction knowledge.gson.fromJson(row.json, Feed::class.java)
            if (row?.status == "running" && row.leaseUntil > time) throw ConnectionFailure("feed_busy")
            if (row != null && (row.retryAt > time || row.attempts >= 3)) throw ConnectionFailure("feed_cooldown")
            knowledge.dao.feed(FeedRow(day, "running", attempts = (row?.attempts ?: 0) + 1, leaseUntil = time + 600_000))
            knowledge.dao.metadata(MetadataRow("feed-owner:$day", owner))
            null
        }
        if (cached != null) return cached
        try {
            return withTimeout(480_000) {
                val profile = knowledge.profile()
                val confirmed = knowledge.knowledge().filter { it.status == "confirmed" }
                val search = source.search(profile, confirmed, today)
                val terms = tokens((confirmed.map { it.name } + profile.interests + profile.focus_areas).joinToString(" "))
                val seen = knowledge.dao.seen().toSet()
                val candidates = knowledge.db.withTransaction {
                    val existing = knowledge.dao.papers().map { knowledge.gson.fromJson(it.json, Paper::class.java) }.toMutableList()
                    ScientificSources.deduplicate(search.papers).map { fresh ->
                        val match = existing.firstOrNull { ScientificSources.identifiers(it).intersect(ScientificSources.identifiers(fresh)).isNotEmpty() }
                        val canonical = if (match == null) fresh else fresh.copy(id = match.id, doi = fresh.doi ?: match.doi, arxiv_id = fresh.arxiv_id ?: match.arxiv_id)
                        knowledge.dao.paper(PaperRow(canonical.id, ScientificSources.doi(canonical.doi), ScientificSources.arxivId(canonical.arxiv_id), knowledge.gson.toJson(canonical)))
                        if (match == null) existing += canonical
                        canonical
                    }
                }
                val selected = candidates.filter { paper ->
                    paper.abstract.isNotBlank() && ScientificSources.identifiers(paper).intersect(seen).isEmpty()
                }.sortedWith(compareByDescending<Paper> { tokens(it.title + " " + it.abstract).intersect(terms).size }.thenByDescending { it.published_at }).take(profile.paper_count)
                val items = selected.map { personalizer.explain(it, settings) }
                val feed = Feed(day, "success", items, search.warnings.joinToString(" ").ifBlank { if (items.isEmpty()) "Keine geeigneten neuen Veröffentlichungen gefunden." else null })
                knowledge.db.withTransaction {
                    if (knowledge.dao.metadata("feed-owner:$day") != owner) throw ConnectionFailure("feed_busy")
                    val current = knowledge.knowledge().filter { it.status == "confirmed" }.map { it.id }.toSet()
                    if (items.any { item -> item.prior_knowledge.any { it.id !in current } }) throw ConnectionFailure("llm_invalid_json")
                    // Persist only complete issues. A failed request never marks papers as seen.
                    selected.forEach { paper ->
                        knowledge.dao.paper(PaperRow(paper.id, ScientificSources.doi(paper.doi), ScientificSources.arxivId(paper.arxiv_id), knowledge.gson.toJson(paper)))
                        ScientificSources.identifiers(paper).forEach { knowledge.dao.seen(SeenRow(it)) }
                    }
                    val row = knowledge.dao.feed(day) ?: throw ConnectionFailure("feed_busy")
                    knowledge.dao.feed(row.copy(status = "success", json = knowledge.gson.toJson(feed), leaseUntil = 0, retryAt = 0))
                    knowledge.changed()
                }
                feed
            }
        } catch (e: Exception) {
            // Finalize the persisted attempt even when the owning ViewModel is cancelled.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                knowledge.db.withTransaction {
                    if (knowledge.dao.metadata("feed-owner:$day") == owner) knowledge.dao.feed(day)?.let { knowledge.dao.feed(it.copy(status = "failed", retryAt = now().toEpochMilli() + 300_000, leaseUntil = 0)) }
                }
            }
            if (e is TimeoutCancellationException) throw ConnectionFailure("feed_timeout")
            if (e is CancellationException) throw e
            throw e
        }
    }
}
