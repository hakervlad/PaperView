package com.example.aiknowledgefeed

import com.example.aiknowledgefeed.backend.api.ScientificSources
import com.example.aiknowledgefeed.backend.services.ConnectionFailure
import org.junit.Assert.*
import org.junit.Test

class ScientificSourcesTest {
    @Test fun openAlexNormalizesAbstractDatesAndIdentifiers() {
        val papers = ScientificSources.parseOpenAlex("""{"results":[{"id":"https://openalex.org/W1","doi":"https://doi.org/10.123/ABC","title":"Bayes Biomarker","publication_date":"2026-11-07","abstract_inverted_index":{"Bayes":[0],"uncertainty":[1]},"authorships":[{"author":{"display_name":"A"}}],"primary_location":{"landing_page_url":"https://example.org/p","pdf_url":"https://example.org/p.pdf"},"locations":[],"primary_topic":{"display_name":"Wissenschaft"}}]}""")
        assertEquals("10.123/abc", papers.single().doi)
        assertEquals("Bayes uncertainty", papers.single().abstract)
        assertEquals("2026-11-07", papers.single().published_at)
    }
    @Test fun arxivUsesPublishedDateAndVersionIndependentId() {
        val xml = """<feed xmlns="http://www.w3.org/2005/Atom"><entry><id>http://arxiv.org/abs/2611.01234v2</id><title>Bayes method</title><summary>Bayes probability</summary><published>2026-11-01T00:00:00Z</published><updated>2026-11-07T00:00:00Z</updated><author><name>A</name></author><category term="cs.AI"/></entry></feed>"""
        val paper = ScientificSources.parseArxiv(xml).single()
        assertEquals("2026-11-01", paper.published_at)
        assertEquals("2611.01234", paper.arxiv_id)
        assertEquals("preprint", paper.review_status)
        assertEquals("https://arxiv.org/abs/2611.01234", paper.url)
    }
    @Test fun xmlEntitiesAndInvalidSchemasAreRejected() {
        for (body in listOf("<!DOCTYPE feed [<!ENTITY x SYSTEM 'file:///private'>]><feed>&x;</feed>", "not-xml")) {
            try { ScientificSources.parseArxiv(body); fail("Untrusted XML") } catch (e: ConnectionFailure) { assertEquals("source_invalid", e.code) }
        }
        try { ScientificSources.parseOpenAlex("{}"); fail("Missing results") } catch (e: ConnectionFailure) { assertEquals("source_invalid", e.code) }
    }
    @Test fun duplicatePapersCarryReliableIdsAcrossSources() {
        val p = com.example.aiknowledgefeed.backend.models.Paper("a", "Title", emptyList(), "Abstract", "2026-11-01", "AI", "openalex", "https://example.org/p", "unknown", doi="10.1/ABC")
        val b = p.copy(id="b", source="arxiv", doi="https://doi.org/10.1/abc", arxiv_id="2611.01234v2")
        val result = ScientificSources.deduplicate(listOf(p,b)).single()
        assertEquals("10.1/abc", result.doi); assertEquals("2611.01234", result.arxiv_id)
        assertTrue(ScientificSources.identifiers(result).contains("arxiv:2611.01234"))
    }
}
