package com.example.aiknowledgefeed.backend.services

import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.repositories.LocalKnowledgeRepository
import com.example.aiknowledgefeed.backend.repositories.localId
import com.google.gson.JsonArray
import com.google.gson.JsonObject

class LocalPersonalizer(private val knowledge: LocalKnowledgeRepository, private val model: ModelClient) {
    suspend fun explain(paper: Paper, settings: Settings): FeedItem {
        val known = knowledge.retrieve(paper.title + " " + paper.abstract)
        val profile = knowledge.profile()
        val context = JsonObject().apply {
            add("paper", JsonObject().apply { addProperty("title", paper.title); addProperty("abstract", paper.abstract.take(12000)) })
            add("profile", JsonObject().apply { addProperty("bachelor_subject", profile.bachelor_subject); add("interests", knowledge.gson.toJsonTree(profile.interests)) })
            add("confirmed_knowledge", JsonArray().apply { known.forEach { k -> add(JsonObject().apply {
                addProperty("id", k.id); addProperty("name", k.name); addProperty("description", k.description.take(600))
            }) } })
        }
        val answer = model.complete(settings, "Schema: {\"title\":str,\"summary\":str,\"concept_ids\":[str],\"new_insight\":str,\"connection\":str,\"relevance\":str,\"takeaway\":str}. Deutscher Titel, Kurzfassung ungefähr 60–120 Wörter. Nur Aussagen aus dem Abstract. Unsicherheiten und Analogien kennzeichnen, unbekannte Begriffe erklären. concept_ids ausschließlich aus confirmed_knowledge. Nur zu diesen IDs Vorwissen behaupten. Ohne passenden Bezug concept_ids=[] und connection=\"\"; fehlenden Bezug offen benennen und Grundlagen erklären. Keine erfundenen Experimente, Zahlen oder Fähigkeiten.", context)
        try {
            fun text(key: String, limit: Int = 6000): String = answer.get(key).asString.also { require(it.isNotBlank() && it.length <= limit) }
            val ids = answer.getAsJsonArray("concept_ids").map { it.asString }.distinct()
            require(ids.size <= 8 && ids.all { id -> known.any { it.id == id } })
            val current = knowledge.knowledge().filter { it.status == "confirmed" }.map { it.id }.toSet()
            require(ids.all { it in current })
            val connection = answer.get("connection").asString.also { require(it.length <= 6000 && (ids.isEmpty() == it.isBlank())) }
            val summary = text("summary")
            return FeedItem(localId(), paper.id, text("title", 500), "Auf Grundlage des Abstracts: " + (if (ids.isEmpty()) "Kein direkter Wissensbezug gespeichert. " else "") + summary,
                known.filter { it.id in ids }, text("new_insight"), connection, text("relevance"), text("takeaway"), true, paper.url, paper.published_at, paper.field)
        } catch (e: ConnectionFailure) { throw e }
        catch (_: Exception) { throw ConnectionFailure("llm_invalid_json") }
    }
}
