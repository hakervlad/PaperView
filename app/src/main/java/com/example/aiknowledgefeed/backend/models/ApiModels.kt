package com.example.aiknowledgefeed.backend.models

import com.example.aiknowledgefeed.frontend.demo.*

data class Profile(val bachelor_subject: String = "", val focus_areas: List<String> = emptyList(), val interests: List<String> = emptyList(), val familiar_topics: List<String> = emptyList(), val paper_count: Int = 7, val lookback_days: Int = 14)
data class Knowledge(val id: String, val name: String, val description: String, val status: String, val connections: List<String> = emptyList(), val source_paper_id: String? = null, val document_ids: List<String> = emptyList())
data class Document(val id: String, val filename: String, val status: String, val message: String? = null, val concepts: List<Knowledge> = emptyList())
data class Feed(val date: String = "", val status: String = "empty", val items: List<FeedItem> = emptyList(), val message: String? = null, val next_retry_at: String? = null)
data class FeedItem(val id: String, val paper_id: String, val title: String, val summary: String, val prior_knowledge: List<Knowledge> = emptyList(), val new_insight: String, val connection: String, val relevance: String, val takeaway: String, val abstract_based: Boolean, val source_url: String, val published_at: String, val category: String)
data class Paper(val id: String, val title: String, val authors: List<String>, val abstract: String, val published_at: String, val field: String, val source: String, val url: String, val review_status: String)
data class KnowledgeUpdate(val name: String, val description: String, val status: String)
data class Generation(val timezone: String)
data class Consent(val consent: Boolean)
data class ConnectionResult(val ok: Boolean, val message: String)
data class Settings(val backendUrl: String = "https://", val token: String = "", val provider: String = "Server", val model: String = "", val llmBaseUrl: String = "", val apiKey: String = "")
data class Snapshot(val feed: Feed = Feed(), val knowledge: List<Knowledge> = emptyList(), val profile: Profile = Profile(), val documents: List<Document> = emptyList())
fun FeedItem.asArticle() = DemoArticle(id = paper_id, category = DemoCategory.entries.firstOrNull { it.label == category } ?: DemoCategory.Science, title = title, summary = summary, priorKnowledge = prior_knowledge.map { it.name }, topic = title, detail = new_insight + "\n\nWarum ist das relevant?\n" + relevance, connection = connection, takeaway = takeaway, date = published_at, sourceUrl = source_url, abstractBased = abstract_based, isDemo = false)
fun Knowledge.asCard() = DemoKnowledge(id, name, DemoCategory.Science, description, connections, source_paper_id)
