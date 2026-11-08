package com.example.aiknowledgefeed

import com.example.aiknowledgefeed.backend.api.ScientificSources
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate

/** Opt-in public metadata only; never a model call or personal knowledge upload. */
class LiveScientificSourcesTest {
    @Test fun shippingKotlinClientsReachPublicPaperApisOverHttps() = runBlocking {
        assumeTrue("Explicit live source probe disabled", System.getenv("PAPERVIEW_LIVE_SOURCES") == "1")
        val source = ScientificSources(); val end = LocalDate.now(); val start = end.minusDays(30)
        val openalex = source.openAlex("artificial intelligence", start, end)
        println("Live OpenAlex: successful HTTPS response, parsed papers=${openalex.size}")
        openalex.forEach { assertTrue(it.url.startsWith("https://")); LocalDate.parse(it.published_at) }
        val arxiv = source.arxiv("transformer", start, end)
        println("Live arXiv: successful HTTPS response, parsed papers=${arxiv.size}")
        arxiv.forEach { assertTrue(it.url.startsWith("https://")); LocalDate.parse(it.published_at); assertEquals("preprint", it.review_status) }
    }
}
