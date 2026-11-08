package com.example.aiknowledgefeed.backend.api

import com.example.aiknowledgefeed.backend.models.*
import com.example.aiknowledgefeed.backend.repositories.tokens
import com.example.aiknowledgefeed.backend.services.*
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import java.io.IOException
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

interface PaperSource {
    suspend fun search(profile: Profile, known: List<Knowledge>, day: LocalDate): PaperSearchResult
}
data class PaperSearchResult(val papers: List<Paper>, val warnings: List<String> = emptyList())

class ScientificSources(private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS)
    .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()) : PaperSource {
    private suspend fun request(url: String, params: Map<String, String>): String {
        val request = Request.Builder().url(url.toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build())
            .header("User-Agent", "PaperView/1.0 (private scientific reading app)").build()
        try {
            val response = client.fetch(request, 4 * 1024 * 1024)
            if (response.status == 429) throw ConnectionFailure("source_rate_limit")
            if (response.status !in 200..299) throw ConnectionFailure("source_unavailable")
            return response.bytes.toString(Charsets.UTF_8)
        } catch (e: CancellationException) { throw e }
        catch (e: ConnectionFailure) { throw e }
        catch (_: IOException) { throw ConnectionFailure("source_unavailable") }
    }
    suspend fun openAlex(query: String, start: LocalDate, end: LocalDate): List<Paper> = parseOpenAlex(request("https://api.openalex.org/works", mapOf(
        "filter" to "from_publication_date:$start,to_publication_date:$end", "per_page" to "30", "sort" to "publication_date:desc", "search" to query.take(300),
    )))
    suspend fun arxiv(query: String, start: LocalDate, end: LocalDate): List<Paper> = arxivMutex.withLock {
        val wait = arxivNext - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        try {
            val terms = tokens(query).take(12).joinToString(" OR ") { "all:$it" }
            val fmt = DateTimeFormatter.BASIC_ISO_DATE
            val search = (if (terms.isBlank()) "" else "($terms) AND ") + "submittedDate:[${start.format(fmt)}0000 TO ${end.format(fmt)}2359]"
            parseArxiv(request("https://export.arxiv.org/api/query", mapOf("search_query" to search, "max_results" to "30", "sortBy" to "submittedDate", "sortOrder" to "descending")))
        } finally { arxivNext = System.currentTimeMillis() + 3000 }
    }
    override suspend fun search(profile: Profile, known: List<Knowledge>, day: LocalDate): PaperSearchResult {
        val query = (listOf(profile.bachelor_subject) + profile.focus_areas + profile.interests + known.filter { it.status == "confirmed" }.take(6).map { it.name }).filter { it.isNotBlank() }.joinToString(" ").take(300)
        if (query.isBlank()) throw ConnectionFailure("profile_required")
        val start = day.minusDays(profile.lookback_days.toLong())
        val all = mutableListOf<Paper>(); val warnings = mutableListOf<String>()
        var error: ConnectionFailure? = null; var successes = 0
        try { all += openAlex(query, start, day); successes++ }
        catch (e: ConnectionFailure) { error = e; warnings += "OpenAlex: ${FailureMessages.message(e.code)}" }
        try { all += arxiv(query, start, day); successes++ }
        catch (e: ConnectionFailure) { error = e; warnings += "arXiv: ${FailureMessages.message(e.code)}" }
        val valid = deduplicate(all.filter { runCatching { LocalDate.parse(it.published_at) in start..day }.getOrDefault(false) })
        if (successes == 0 || (valid.isEmpty() && error != null)) throw error ?: ConnectionFailure("source_unavailable")
        return PaperSearchResult(valid, warnings)
    }
    companion object {
        private val arxivMutex = Mutex()
        private var arxivNext = 0L
        private fun id(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
        fun doi(value: String?): String? = value?.replace(Regex("^(https?://(dx\\.)?doi.org/|doi:\\s*)", RegexOption.IGNORE_CASE), "")?.trim()?.lowercase(Locale.ROOT)?.ifBlank { null }
        fun arxivId(value: String?): String? = Regex("(\\d{4}\\.\\d{4,5}|[a-z-]+(?:\\.[A-Z]{2})?/\\d{7})(?:v\\d+)?", RegexOption.IGNORE_CASE).find(value.orEmpty())?.groupValues?.get(1)?.lowercase(Locale.ROOT)
        fun identifiers(paper: Paper): Set<String> = buildSet {
            add("id:" + paper.id)
            doi(paper.doi)?.let { add("doi:$it") }; arxivId(paper.arxiv_id)?.let { add("arxiv:$it") }
            if (size == 1) add("title:" + paper.title.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), ""))
        }
        fun deduplicate(papers: List<Paper>): List<Paper> {
            val groups = mutableListOf<Pair<MutableSet<String>, Paper>>()
            papers.forEach { raw ->
                val p = raw.copy(doi = doi(raw.doi), arxiv_id = arxivId(raw.arxiv_id))
                val ids = identifiers(p); val matches = groups.filter { it.first.intersect(ids).isNotEmpty() }
                if (matches.isEmpty()) groups += ids.toMutableSet() to p
                else {
                    val first = matches.first()
                    groups.removeAll(matches.toSet())
                    val merged = first.second.copy(doi = first.second.doi ?: p.doi, arxiv_id = first.second.arxiv_id ?: p.arxiv_id)
                    groups += (matches.flatMap { it.first }.toMutableSet().apply { addAll(ids) }) to merged
                }
            }
            return groups.map { it.second }
        }
        private fun safeUrl(value: String?): String? = value?.takeIf { runCatching { it.toHttpUrl().let { u -> u.isHttps && u.username.isEmpty() && u.password.isEmpty() } }.getOrDefault(false) }
        fun parseOpenAlex(raw: String): List<Paper> = try {
            JsonParser.parseString(raw).asJsonObject.getAsJsonArray("results").also { require(it.size() <= 100) }.map { element ->
                val w = element.asJsonObject
                fun string(key: String) = w.get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                val loc = w.get("primary_location")?.takeIf { it.isJsonObject }?.asJsonObject
                val abstract = w.get("abstract_inverted_index")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.flatMap { (word, positions) ->
                    positions.asJsonArray.map { it.asInt to word }.filter { it.first in 0..9999 }
                }?.sortedBy { it.first }?.joinToString(" ") { it.second }?.take(16000).orEmpty()
                val sourceId = string("id"); val title = string("title"); require(sourceId.isNotBlank() && title.isNotBlank())
                val arxiv = w.getAsJsonArray("locations")?.mapNotNull { it.asJsonObject.get("landing_page_url")?.takeUnless { it.isJsonNull }?.asString }?.mapNotNull { arxivId(it) }?.firstOrNull()
                Paper(id(sourceId), title.take(500), w.getAsJsonArray("authorships")?.take(100)?.mapNotNull { it.asJsonObject.getAsJsonObject("author")?.get("display_name")?.takeUnless { it.isJsonNull }?.asString } ?: emptyList(),
                    abstract, string("publication_date"), "Wissenschaft", "openalex", safeUrl(loc?.get("landing_page_url")?.takeUnless { it.isJsonNull }?.asString) ?: safeUrl(string("doi")) ?: safeUrl(sourceId) ?: throw IllegalArgumentException(),
                    "unknown", doi(string("doi")), arxiv, safeUrl(loc?.get("pdf_url")?.takeUnless { it.isJsonNull }?.asString))
            }
        } catch (_: Exception) { throw ConnectionFailure("source_invalid") }
        fun parseArxiv(raw: String): List<Paper> = try {
            require(!raw.contains("<!DOCTYPE", ignoreCase = true) && !raw.contains("<!ENTITY", ignoreCase = true))
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true; isExpandEntityReferences = false }
            val root = factory.newDocumentBuilder().parse(raw.byteInputStream()).documentElement
            require(root.localName == "feed")
            val entries = root.getElementsByTagNameNS("http://www.w3.org/2005/Atom", "entry"); require(entries.length <= 100)
            (0 until entries.length).map { index ->
                val entry = entries.item(index) as Element
                fun text(name: String) = entry.getElementsByTagNameNS("http://www.w3.org/2005/Atom", name).item(0)?.textContent.orEmpty().trim()
                val aid = arxivId(text("id")) ?: throw IllegalArgumentException()
                val authors = entry.getElementsByTagNameNS("http://www.w3.org/2005/Atom", "name")
                val doi = entry.getElementsByTagNameNS("http://arxiv.org/schemas/atom", "doi").item(0)?.textContent
                Paper(id("arxiv:$aid"), text("title").replace(Regex("\\s+"), " ").take(500), (0 until authors.length).take(100).map { authors.item(it).textContent }, text("summary").take(16000), text("published").take(10),
                    "Wissenschaft", "arxiv", "https://arxiv.org/abs/$aid", "preprint", doi(doi), aid, "https://arxiv.org/pdf/$aid")
            }
        } catch (_: Exception) { throw ConnectionFailure("source_invalid") }
    }
}
