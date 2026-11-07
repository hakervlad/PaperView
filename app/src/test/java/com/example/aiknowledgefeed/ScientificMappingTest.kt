package com.example.aiknowledgefeed

import com.example.aiknowledgefeed.backend.models.*
import org.junit.Assert.*
import org.junit.Test

class ScientificMappingTest {
    private fun item(prior: List<Knowledge>) = FeedItem("explanation-1", "paper-1", "Erkenntnis", "Zusammenfassung", prior, "Abstractgestützte Aussage", "Bezug", "Relevanz", "Merksatz", true, "https://arxiv.org/abs/2601.00001", "2026-01-01", "Wissenschaft")
    @Test fun liveMappingPreservesProvenanceAndEvidence() {
        val article = item(listOf(Knowledge("concept-1", "APIs", "Schnittstellen", "confirmed"))).asArticle()
        assertEquals("paper-1", article.id)
        assertEquals(listOf("APIs"), article.priorKnowledge)
        assertEquals("https://arxiv.org/abs/2601.00001", article.sourceUrl)
        assertTrue(article.abstractBased)
        assertFalse(article.isDemo)
    }
    @Test fun absentPriorKnowledgeDoesNotUseDemoConcepts() { assertTrue(item(emptyList()).asArticle().priorKnowledge.isEmpty()) }
}
